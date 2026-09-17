package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasPixTransactionResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.PlatformPixKey;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PlatformPixKeyRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Resolves the destination of an inbound Pix transaction.
 *
 * Destination classification is explicit:
 * - SUBACCOUNT: destination key exists in the Theron organization Pix registry;
 * - PLATFORM: destination key exists in the platform Master Pix registry;
 * - EXTERNAL: destination key is not registered by Theron.
 *
 * The platform Master is intentionally not represented by Account MAIN. Account
 * MAIN belongs to the owner of an organization, while the Master is a separate
 * platform-level entity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundPixDestinationResolver {

    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final PixKeyRepository pixKeyRepository;
    private final PlatformPixKeyRepository platformPixKeyRepository;
    private final SubaccountRepository subaccountRepository;

    /**
     * Legacy subaccount-only API retained for existing callers.
     */
    public Subaccount resolve(AsaasWebhookPayload payload) {
        Resolution resolution = classify(payload);
        return resolution.isSubaccount() ? resolution.subaccount() : null;
    }

    /**
     * Explicit destination classification API. The name intentionally differs
     * from the legacy resolve() method so callers cannot accidentally assign a
     * Subaccount-returning method to Resolution.
     */
    public Resolution classify(AsaasWebhookPayload payload) {
        if (payload == null || payload.getPayment() == null) {
            return Resolution.external(null);
        }

        String pixTransactionId = extractPixTransactionId(payload.getPayment().getPixTransaction());
        if (pixTransactionId == null || pixTransactionId.isBlank()) {
            log.warn("Inbound Pix payment has no pixTransaction id: paymentId={}",
                    payload.getPayment().getId());
            return Resolution.external(null);
        }

        String apiKey = asaasApiKeyResolver.resolveForOutbound(null);
        AsaasPixTransactionResponse pixTransaction;
        try {
            pixTransaction = asaasPaymentClient.retrievePixTransaction(apiKey, pixTransactionId);
        } catch (RuntimeException ex) {
            log.warn("Could not retrieve Asaas Pix transaction for inbound payment: pixTransactionId={}",
                    pixTransactionId, ex);
            return Resolution.external(null);
        }

        if (pixTransaction == null) {
            log.warn("Asaas returned an empty Pix transaction: pixTransactionId={}", pixTransactionId);
            return Resolution.external(null);
        }

        String transferId = pixTransaction.getTransferId();
        String transactionAddressKey = pixTransaction.getAddressKey();
        String externalAddressKey = pixTransaction.getExternalAccount() != null
                ? pixTransaction.getExternalAccount().getAddressKey()
                : null;

        log.info(
                "Inbound Pix transaction details: pixTransactionId={}, status={}, transferId={}, addressKey={}, externalAccountAddressKey={}",
                pixTransactionId,
                pixTransaction.getStatus(),
                transferId,
                transactionAddressKey,
                externalAddressKey);

        if (transferId != null && !transferId.isBlank()) {
            try {
                AsaasTransferResponse transfer = asaasTransferClient.retrieveTransfer(apiKey, transferId);
                String transferAddressKey = transfer != null ? transfer.getPixAddressKey() : null;

                log.info("Inbound Pix originating transfer details: transferId={}, pixAddressKey={}, status={}",
                        transferId,
                        transferAddressKey,
                        transfer != null ? transfer.getStatus() : null);

                Resolution resolved = resolveByKey(transferAddressKey);
                if (!resolved.isExternal()) {
                    return resolved;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not retrieve originating Asaas transfer for inbound Pix: transferId={}",
                        transferId, ex);
            }
        }

        Resolution resolved = resolveByKey(transactionAddressKey);
        if (!resolved.isExternal()) {
            return resolved;
        }

        resolved = resolveByKey(externalAddressKey);
        if (!resolved.isExternal()) {
            return resolved;
        }

        String candidate = firstNonBlank(transactionAddressKey, externalAddressKey);
        log.info("Inbound Pix destination key is not registered by Theron; treating as external: key={}", candidate);
        return Resolution.external(candidate);
    }

    private Resolution resolveByKey(String key) {
        if (key == null || key.isBlank()) {
            return Resolution.external(key);
        }

        PlatformPixKey platformKey = platformPixKeyRepository
                .findByKeyAndStatus(key, PixKeyStatus.ACTIVE)
                .orElse(null);
        if (platformKey != null) {
            log.info("Resolved inbound Pix destination as Theron platform Master: key={}", key);
            return Resolution.platform(platformKey, key);
        }

        PixKey pixKey = pixKeyRepository.findByKeyAndStatus(key, PixKeyStatus.ACTIVE).orElse(null);
        if (pixKey == null || pixKey.getAccount() == null || pixKey.getAccount().getId() == null) {
            return Resolution.external(key);
        }

        Subaccount subaccount = subaccountRepository
                .findByAccount_Id(pixKey.getAccount().getId())
                .orElse(null);

        if (subaccount != null) {
            log.info("Resolved inbound Pix destination by Theron Pix key: key={}, subaccountId={}",
                    key, subaccount.getId());
            return Resolution.subaccount(subaccount, key);
        }

        log.warn("Pix key is registered in pix_key but has no subaccount owner; treating as external: key={}", key);
        return Resolution.external(key);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String extractPixTransactionId(Object pixTransaction) {
        if (pixTransaction instanceof String id && !id.isBlank()) {
            return id;
        }
        if (pixTransaction instanceof Map<?, ?> map && map.get("id") != null) {
            String id = String.valueOf(map.get("id"));
            return id.isBlank() ? null : id;
        }
        return null;
    }

    public enum DestinationType {
        SUBACCOUNT,
        PLATFORM,
        EXTERNAL
    }

    public record Resolution(
            DestinationType type,
            Subaccount subaccount,
            PlatformPixKey platformPixKey,
            String pixKey) {

        public static Resolution subaccount(Subaccount subaccount, String pixKey) {
            return new Resolution(DestinationType.SUBACCOUNT, subaccount, null, pixKey);
        }

        public static Resolution platform(PlatformPixKey platformPixKey, String pixKey) {
            return new Resolution(DestinationType.PLATFORM, null, platformPixKey, pixKey);
        }

        public static Resolution external(String pixKey) {
            return new Resolution(DestinationType.EXTERNAL, null, null, pixKey);
        }

        public boolean isSubaccount() {
            return type == DestinationType.SUBACCOUNT;
        }

        public boolean isPlatform() {
            return type == DestinationType.PLATFORM;
        }

        public boolean isExternal() {
            return type == DestinationType.EXTERNAL;
        }
    }
}
