package com.theron.wallet.service.impl;

import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import com.theron.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Prevents Master→Theron double credit: Platform PIX already credited the destination,
 * then PAYMENT_RECEIVED / inbound-reconcile must not credit again with pay_xxx.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PlatformPixInboundDedupe {

    static final int LOOKBACK_HOURS = 24;
    private static final String PLATFORM_PIX_IN_PREFIX = "asaas:platform-pix:in:%";

    private final PlatformPixTransferRepository platformPixTransferRepository;
    private final PixKeyRepository pixKeyRepository;
    private final TransactionRepository transactionRepository;

    /**
     * @param relatedResourceId optional pixTransaction id or transfer id from the payment payload
     * @param paymentDescription Asaas payment description (used to safely apply key+amount fallback)
     */
    boolean alreadyCreditedByPlatform(
            Subaccount destination,
            BigDecimal amount,
            String relatedResourceId,
            String paymentDescription) {
        if (destination == null || amount == null || amount.signum() <= 0) {
            return false;
        }

        if (relatedResourceId != null && !relatedResourceId.isBlank()) {
            if (matchedViaPixTransactionId(relatedResourceId)
                    || matchedViaTransferId(relatedResourceId)
                    || creditedViaPlatformIdempotency(relatedResourceId)) {
                log.info("Inbound PIX skipped: already credited via platform_pix_transfer resourceId={}",
                        relatedResourceId);
                return true;
            }
        }

        if (!looksLikeAutoChargeFromReceivedPix(paymentDescription)) {
            return false;
        }
        if (destination.getAccount() == null) {
            return false;
        }
        UUID accountId = destination.getAccount().getId();
        LocalDateTime since = LocalDateTime.now().minusHours(LOOKBACK_HOURS);

        if (hasRecentPlatformPixCredit(accountId, amount, since)) {
            log.info(
                    "Inbound PIX skipped: already credited via asaas:platform-pix:in amount={} accountId={}",
                    amount, accountId);
            return true;
        }

        List<PixKey> keys = pixKeyRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        for (PixKey key : keys) {
            if (key.getStatus() != PixKeyStatus.ACTIVE || key.getKey() == null || key.getKey().isBlank()) {
                continue;
            }
            // COMPLETED without creditTransactionId still means Master credit is in flight / done
            List<PlatformPixTransfer> matches = platformPixTransferRepository
                    .findByDestinationKeyAndAmountSince(
                            key.getKey(), amount, TransactionStatus.COMPLETED, since);
            if (!matches.isEmpty()) {
                log.info(
                        "Inbound PIX skipped: already credited via platform_pix_transfer key={} amount={} accountId={}",
                        key.getKey(), amount, accountId);
                return true;
            }
        }
        return false;
    }

    static boolean looksLikeAutoChargeFromReceivedPix(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        String normalized = description.toLowerCase(Locale.ROOT);
        return normalized.contains("cobrança gerada automaticamente")
                || normalized.contains("cobranca gerada automaticamente")
                || normalized.contains("platform pix");
    }

    private boolean hasRecentPlatformPixCredit(UUID accountId, BigDecimal amount, LocalDateTime since) {
        return !transactionRepository.findRecentByAccountTypeStatusAmountAndIdempotencyPrefix(
                accountId,
                TransactionType.TRANSFER_IN,
                TransactionStatus.COMPLETED,
                amount,
                PLATFORM_PIX_IN_PREFIX,
                since).isEmpty();
    }

    /** COMPLETED (even mid-persist of creditTransactionId) or already linked credit. */
    private boolean isPlatformCreditInEffect(PlatformPixTransfer row) {
        return row.getCreditTransactionId() != null
                || row.getStatus() == TransactionStatus.COMPLETED;
    }

    private boolean matchedViaPixTransactionId(String pixTxId) {
        return platformPixTransferRepository.findByAsaasPixTransactionId(pixTxId)
                .filter(this::isPlatformCreditInEffect)
                .isPresent();
    }

    private boolean matchedViaTransferId(String transferId) {
        return platformPixTransferRepository.findByAsaasTransferId(transferId)
                .filter(this::isPlatformCreditInEffect)
                .isPresent();
    }

    private boolean creditedViaPlatformIdempotency(String resourceId) {
        String key = "asaas:platform-pix:in:" + resourceId;
        return transactionRepository.findByIdempotencyKey(key).isPresent()
                || transactionRepository.findByAsaasPaymentId(resourceId).isPresent();
    }
}
