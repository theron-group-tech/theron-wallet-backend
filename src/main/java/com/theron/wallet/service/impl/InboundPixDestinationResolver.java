package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasPixTransactionResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves the Theron subaccount that received an inbound Pix transaction.
 *
 * PAYMENT_RECEIVED webhooks can arrive with the platform/root Asaas account
 * even when the Pix destination is one of our linked subaccounts. In that
 * situation the webhook account.id alone is not enough to identify the wallet.
 * We therefore inspect the Pix transaction and, when necessary, its originating
 * transfer, then match the destination Pix key against our own PixKey registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundPixDestinationResolver {

    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final PixKeyRepository pixKeyRepository;
    private final SubaccountRepository subaccountRepository;

    public Subaccount resolve(AsaasWebhookPayload payload) {
        if (payload == null || payload.getPayment() == null) {
            return null;
        }

        String pixTransactionId = WebhookServiceImpl.extractPixTransactionId(
                payload.getPayment().getPixTransaction());
        if (pixTransactionId == null || pixTransactionId.isBlank()) {
            return null;
        }

        String apiKey = asaasApiKeyResolver.resolveForOutbound(null);
        AsaasPixTransactionResponse pixTransaction;
        try {
            pixTransaction = asaasPaymentClient.retrievePixTransaction(apiKey, pixTransactionId);
        } catch (RuntimeException ex) {
            log.warn("Could not retrieve Asaas Pix transaction for inbound payment: pixTransactionId={}",
                    pixTransactionId, ex);
            return null;
        }

        Subaccount resolved = findByPixKey(pixTransaction != null ? pixTransaction.getAddressKey() : null);
        if (resolved != null) {
            return resolved;
        }

        String transferId = pixTransaction != null ? pixTransaction.getTransferId() : null;
        if (transferId == null || transferId.isBlank()) {
            return null;
        }

        try {
            AsaasTransferResponse transfer = asaasTransferClient.retrieveTransfer(apiKey, transferId);
            return findByPixKey(transfer != null ? transfer.getPixAddressKey() : null);
        } catch (RuntimeException ex) {
            log.warn("Could not retrieve originating Asaas transfer for inbound Pix: transferId={}",
                    transferId, ex);
            return null;
        }
    }

    private Subaccount findByPixKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        PixKey pixKey = pixKeyRepository.findByKeyAndStatus(key, PixKeyStatus.ACTIVE).orElse(null);
        if (pixKey == null || pixKey.getAccount() == null || pixKey.getAccount().getId() == null) {
            return null;
        }

        Subaccount subaccount = subaccountRepository
                .findByAccount_Id(pixKey.getAccount().getId())
                .orElse(null);

        if (subaccount != null) {
            log.info("Resolved inbound Pix destination by Pix key: key={}, subaccountId={}",
                    key, subaccount.getId());
        }
        return subaccount;
    }
}
