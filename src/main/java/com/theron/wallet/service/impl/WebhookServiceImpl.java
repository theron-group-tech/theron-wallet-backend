package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AsaasPaymentEvent;
import com.theron.wallet.enums.AsaasTransferEvent;
import com.theron.wallet.enums.AsaasWebhookEventStatus;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.PaymentOrderStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.repository.PaymentOrderRepository;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.InboundPixCreditService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.AsaasOnboardingService;
import com.theron.wallet.service.WalletService;
import com.theron.wallet.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final TransactionRepository transactionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final InboundPixCreditService inboundPixCreditService;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final NotificationService notificationService;
    private static final Set<String> PAYMENT_CONFIRMED_REMOTE = Set.of(
            "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");
    private static final Set<String> PAYMENT_REVERSED_REMOTE = Set.of(
            "REFUNDED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE");
    private static final Set<String> TRANSFER_DONE_REMOTE = Set.of("DONE");
    private static final Set<String> TRANSFER_FAILED_REMOTE = Set.of("FAILED", "CANCELLED", "BLOCKED");

    private final AsaasProperties asaasProperties;
    private final SubaccountRepository subaccountRepository;
    private final AsaasWebhookEventPersister asaasWebhookEventPersister;
    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasOnboardingService asaasOnboardingService;

    @Override
    @Transactional
    public void receive(String token, AsaasWebhookPayload payload) {
        if (!isRecognizedWebhookToken(token)) {
            throw new UnauthorizedException("Invalid webhook token");
        }

        if (!isGlobalWebhookToken(token) && !tokenMatchesEventContext(token, payload)) {
            log.warn("Webhook token does not belong to the event context; ignoring event={}",
                    payload != null ? payload.getEvent() : null);
            return;
        }

        String eventId = resolveEventId(payload);
        var stored = asaasWebhookEventPersister.claim(eventId, payload);
        if (stored.isEmpty()) {
            log.info("Duplicate Asaas webhook ignored: eventId={}", eventId);
            return;
        }

        try {
            if (payload.getEvent() != null && payload.getEvent().startsWith("ACCOUNT_STATUS_")) {
                processAccountStatusWebhook(payload);
            } else if (payload.getEvent() != null && payload.getEvent().startsWith("TRANSFER_")) {
                processTransferWebhook(payload);
            } else {
                processPaymentWebhook(payload, token);
            }
            asaasWebhookEventPersister.mark(stored.get(), AsaasWebhookEventStatus.PROCESSED);
        } catch (RuntimeException ex) {
            asaasWebhookEventPersister.mark(stored.get(), AsaasWebhookEventStatus.FAILED);
            throw ex;
        }
    }

    private boolean isRecognizedWebhookToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return isGlobalWebhookToken(token) || subaccountRepository.findByWebhookToken(token).isPresent();
    }

    private boolean isGlobalWebhookToken(String token) {
        String expectedToken = asaasProperties.getWebhookToken();
        return expectedToken != null && !expectedToken.isBlank()
                && MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private boolean tokenMatchesEventContext(String token, AsaasWebhookPayload payload) {
        if (tokenMatchesSubaccountAccountStatus(token, payload)) {
            return true;
        }
        return tokenMatchesOwnerTransaction(token, payload);
    }

    private boolean tokenMatchesSubaccountAccountStatus(String token, AsaasWebhookPayload payload) {
        return subaccountRepository.findByWebhookToken(token)
                .filter(subaccount -> {
                    String asaasAccountId = payload != null && payload.getAccount() != null
                            ? payload.getAccount().getId() : null;
                    return asaasAccountId == null || asaasAccountId.equals(subaccount.getAsaasAccountId());
                })
                .isPresent();
    }

    private boolean tokenMatchesOwnerTransaction(String token, AsaasWebhookPayload payload) {
        String resourceId = AsaasWebhookEventPersister.resourceId(payload);
        if (resourceId == null) {
            return false;
        }
        Optional<Transaction> transaction = transactionRepository.findByAsaasPaymentId(resourceId);
        if (transaction.isEmpty()) {
            return false;
        }
        Subaccount owner = ownerSubaccount(transaction.get());
        if (owner == null || owner.getWebhookToken() == null || owner.getWebhookToken().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                owner.getWebhookToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private Subaccount ownerSubaccount(Transaction transaction) {
        if (transaction.getWallet() != null && transaction.getWallet().getSubaccount() != null) {
            return transaction.getWallet().getSubaccount();
        }
        if (transaction.getAccount() != null) {
            return subaccountRepository.findByAccount_Id(transaction.getAccount().getId()).orElse(null);
        }
        return null;
    }

    static String resolveEventId(AsaasWebhookPayload payload) {
        if (payload.getId() != null && !payload.getId().isBlank()) {
            return payload.getId();
        }
        String resourceId = AsaasWebhookEventPersister.resourceId(payload);
        String event = payload.getEvent() != null ? payload.getEvent() : "UNKNOWN";
        if (resourceId != null) {
            return event + ":" + resourceId;
        }
        return event + ":unknown";
    }

    @Override
    @Transactional
    public void processPaymentWebhook(AsaasWebhookPayload payload) {
        processPaymentWebhook(payload, null);
    }

    private void processPaymentWebhook(AsaasWebhookPayload payload, String webhookToken) {
        String eventName = payload.getEvent();
        AsaasWebhookPayload.Payment payment = payload.getPayment();

        if (payment == null || payment.getId() == null) {
            log.warn("Webhook received with null payment data, ignoring. event={}", eventName);
            return;
        }

        log.info("Processing webhook: event={}, paymentId={}", eventName, payment.getId());

        AsaasPaymentEvent event;
        try {
            event = AsaasPaymentEvent.valueOf(eventName);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Asaas payment event: {}, ignoring", eventName);
            return;
        }

        Optional<Transaction> transactionOpt = transactionRepository.findByAsaasPaymentIdForUpdate(payment.getId());

        if (transactionOpt.isEmpty()) {
            if (event.isConfirmation()) {
                creditInboundPixPayment(payload, payment, webhookToken);
            } else {
                log.warn("No transaction found for asaasPaymentId={}, ignoring webhook", payment.getId());
            }
            return;
        }

        Transaction transaction = transactionOpt.get();

        if (event.isConfirmation()) {
            handlePaymentConfirmation(transaction);
        } else if (event.isReversal()) {
            handlePaymentReversal(transaction, event);
        } else if (event.isCancellation()) {
            handlePaymentCancellation(transaction, event);
        } else {
            log.info("Webhook event {} does not require action for transactionId={}",
                    eventName, transaction.getId());
        }
    }

    private void creditInboundPixPayment(
            AsaasWebhookPayload payload, AsaasWebhookPayload.Payment payment, String webhookToken) {
        Subaccount subaccount = resolveInboundSubaccount(payload, webhookToken);
        if (subaccount == null) {
            log.error("Inbound PIX confirmation without resolvable subaccount: paymentId={}, event={}, hasAccountId={}",
                    payment.getId(),
                    payload.getEvent(),
                    payload.getAccount() != null && payload.getAccount().getId() != null);
            throw new IllegalStateException(
                    "Cannot credit inbound PIX: subaccount not resolved for paymentId=" + payment.getId());
        }

        if (alreadyCreditedViaRelatedId(payment)) {
            log.info("Inbound PIX already credited via related resource id: paymentId={}", payment.getId());
            return;
        }

        BigDecimal amount = payment.getValue() != null ? payment.getValue() : payment.getNetValue();
        inboundPixCreditService.credit(
                subaccount,
                payment.getId(),
                amount,
                inboundPixDescription(payment),
                inboundPixReference(payment));
    }

    private boolean alreadyCreditedViaRelatedId(AsaasWebhookPayload.Payment payment) {
        String pixTxId = extractPixTransactionId(payment.getPixTransaction());
        if (pixTxId == null || pixTxId.isBlank() || pixTxId.equals(payment.getId())) {
            return false;
        }
        return transactionRepository.findByAsaasPaymentId(pixTxId).isPresent()
                || transactionRepository.findByIdempotencyKey(
                        InboundPixCreditService.idempotencyKey(pixTxId)).isPresent();
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

    private Subaccount resolveInboundSubaccount(AsaasWebhookPayload payload, String webhookToken) {
        String asaasAccountId = payload.getAccount() != null ? payload.getAccount().getId() : null;
        if (asaasAccountId != null && !asaasAccountId.isBlank()) {
            Subaccount byAccount = subaccountRepository.findByAsaasAccountId(asaasAccountId).orElse(null);
            if (byAccount != null) {
                return byAccount;
            }
        }
        if (webhookToken != null && !webhookToken.isBlank()) {
            return subaccountRepository.findByWebhookToken(webhookToken).orElse(null);
        }
        return null;
    }

    private static String inboundPixDescription(AsaasWebhookPayload.Payment payment) {
        if (payment.getDescription() != null && !payment.getDescription().isBlank()) {
            return payment.getDescription();
        }
        return "PIX recebido via Asaas";
    }

    private static String inboundPixReference(AsaasWebhookPayload.Payment payment) {
        String pixTxId = extractPixTransactionId(payment.getPixTransaction());
        if (pixTxId != null) {
            return pixTxId;
        }
        if (payment.getPixQrCodeId() != null && !payment.getPixQrCodeId().isBlank()) {
            return payment.getPixQrCodeId();
        }
        return null;
    }

    private void handlePaymentConfirmation(Transaction transaction) {
        if (!awaitsProvider(transaction)) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        if (!remotePaymentMatches(transaction, PAYMENT_CONFIRMED_REMOTE)) {
            return;
        }
        transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        syncPixTransaction(transaction);
        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());

        log.info("Deposit confirmed: transactionId={}, walletId={}, amount={}",
                transaction.getId(), transaction.getWallet().getId(), transaction.getAmount());
        UUID orgId = resolveOrganizationId(transaction);
        notificationService.notifyUsersWithPermission(
                orgId,
                PermissionCodes.WALLET_READ,
                null,
                NotificationType.PIX_RECEIVED,
                transaction.getId(),
                amountData(transaction));
    }

    private void handlePaymentCancellation(Transaction transaction, AsaasPaymentEvent event) {
        if (!awaitsProvider(transaction)) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        TransactionStatus newStatus = (event == AsaasPaymentEvent.PAYMENT_DELETED)
                ? TransactionStatus.CANCELLED
                : TransactionStatus.FAILED;
        transactionLifecycleService.transition(transaction, newStatus);
        syncPixTransaction(transaction);

        log.info("Deposit {}: transactionId={}, event={}",
                newStatus.name().toLowerCase(), transaction.getId(), event.getValue());
    }

    private void handlePaymentReversal(Transaction transaction, AsaasPaymentEvent event) {
        if (!remotePaymentMatches(transaction, PAYMENT_REVERSED_REMOTE)) {
            return;
        }
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            transactionLifecycleService.transition(transaction, TransactionStatus.REVERSED);
            syncPixTransaction(transaction);
            walletService.debit(transaction.getWallet().getId(), transaction.getAmount());
            log.info("Deposit reversed: transactionId={}, event={}", transaction.getId(), event.getValue());
            return;
        }
        if (awaitsProvider(transaction)) {
            transactionLifecycleService.transition(transaction, TransactionStatus.FAILED);
            syncPixTransaction(transaction);
            log.info("Deposit failed on reversal event before completion: transactionId={}, event={}",
                    transaction.getId(), event.getValue());
            return;
        }
        log.info("Ignoring reversal for transactionId={}, status={}",
                transaction.getId(), transaction.getStatus());
    }

    private void processAccountStatusWebhook(AsaasWebhookPayload payload) {
        String eventName = payload.getEvent();
        String asaasAccountId = payload.getAccount() != null ? payload.getAccount().getId() : null;
        if (asaasAccountId == null || asaasAccountId.isBlank()) {
            log.warn("Account status webhook without account id, event={}", eventName);
            return;
        }
        log.info("Processing account status webhook: event={}, asaasAccountId={}", eventName, asaasAccountId);
        asaasOnboardingService.applyAccountStatusWebhook(asaasAccountId, eventName);
    }

    @Override
    @Transactional
    public void processTransferWebhook(AsaasWebhookPayload payload) {
        String eventName = payload.getEvent();
        AsaasWebhookPayload.Transfer transfer = payload.getTransfer();

        if (transfer == null || transfer.getId() == null) {
            log.warn("Transfer webhook received with null transfer data, ignoring. event={}", eventName);
            return;
        }

        log.info("Processing transfer webhook: event={}, transferId={}", eventName, transfer.getId());

        AsaasTransferEvent event;
        try {
            event = AsaasTransferEvent.valueOf(eventName);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Asaas transfer event: {}, ignoring", eventName);
            return;
        }

        Optional<Transaction> transactionOpt = transactionRepository.findByAsaasPaymentIdForUpdate(transfer.getId());

        if (transactionOpt.isEmpty()) {
            log.warn("No transaction found for asaasTransferId={}, ignoring webhook", transfer.getId());
            return;
        }

        Transaction transaction = transactionOpt.get();

        if (event.isConfirmation()) {
            handleTransferConfirmation(transaction);
            syncPaymentOrderFromTransfer(transaction, PaymentOrderStatus.COMPLETED);
        } else if (event.isFailure()) {
            handleTransferFailure(transaction, event);
            syncPaymentOrderFromTransfer(transaction, PaymentOrderStatus.FAILED);
        } else {
            log.info("Transfer webhook event {} does not require action for transactionId={}",
                    eventName, transaction.getId());
        }
    }

    private void handleTransferConfirmation(Transaction transaction) {
        if (!awaitsProvider(transaction)) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        if (!remoteTransferMatches(transaction, TRANSFER_DONE_REMOTE)) {
            return;
        }
        transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        syncPixTransaction(transaction);

        log.info("Withdrawal confirmed: transactionId={}, walletId={}, amount={}",
                transaction.getId(), transaction.getWallet().getId(), transaction.getAmount());
        if (transaction.getType() == TransactionType.PIX && transaction.getCreatedBy() != null) {
            notificationService.notify(
                    transaction.getCreatedBy().getId(),
                    resolveOrganizationId(transaction),
                    NotificationType.PIX_SENT,
                    transaction.getId(),
                    amountData(transaction));
        }
    }

    private void handleTransferFailure(Transaction transaction, AsaasTransferEvent event) {
        if (!awaitsProvider(transaction)) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        if (!remoteTransferMatches(transaction, TRANSFER_FAILED_REMOTE)) {
            return;
        }
        TransactionStatus newStatus = (event == AsaasTransferEvent.TRANSFER_CANCELLED)
                ? TransactionStatus.CANCELLED
                : TransactionStatus.FAILED;
        transactionLifecycleService.transition(transaction, newStatus);
        syncPixTransaction(transaction);
        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());

        log.info("Withdrawal {}: transactionId={}, event={}, amount credited back to walletId={}",
                newStatus.name().toLowerCase(), transaction.getId(), event.getValue(),
                transaction.getWallet().getId());
    }

    private void syncPixTransaction(Transaction transaction) {
        if (transaction.getType() != TransactionType.PIX) {
            return;
        }
        pixTransactionRepository.findByTransactionIdForUpdate(transaction.getId()).ifPresent(pixTx -> {
            pixTx.setStatus(transaction.getStatus());
            pixTransactionRepository.save(pixTx);
            log.debug("Synced PixTransaction {} to status {}", pixTx.getId(), pixTx.getStatus());
        });
    }

    private void syncPaymentOrderFromTransfer(Transaction transaction, PaymentOrderStatus nextStatus) {
        if (transaction.getReference() == null || !transaction.getReference().startsWith("payment-order:")) {
            return;
        }
        paymentOrderRepository.findByDebitTransactionId(transaction.getId()).ifPresent(order -> {
            if (order.getStatus() != PaymentOrderStatus.PROCESSING) {
                return;
            }
            order.setStatus(nextStatus);
            if (nextStatus == PaymentOrderStatus.COMPLETED) {
                order.setCompletedAt(LocalDateTime.now());
            }
            paymentOrderRepository.save(order);
            log.info("Synced PaymentOrder {} to {} from transfer webhook transactionId={}",
                    order.getId(), nextStatus, transaction.getId());
        });
    }

    private static boolean awaitsProvider(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING
                || transaction.getStatus() == TransactionStatus.PROCESSING;
    }

    private boolean remotePaymentMatches(Transaction transaction, Set<String> expectedStatuses) {
        String asaasId = transaction.getAsaasPaymentId();
        if (asaasId == null || asaasId.isBlank()) {
            log.warn("Ignoring payment webhook without Asaas id: transactionId={}", transaction.getId());
            return false;
        }
        try {
            AsaasPaymentResponse remote = asaasPaymentClient.retrievePayment(resolveApiKey(transaction), asaasId);
            if (remote == null || !statusIn(remote.getStatus(), expectedStatuses)) {
                log.warn("Ignoring payment webhook; Asaas status incompatible: transactionId={}, remoteStatus={}",
                        transaction.getId(), remote != null ? remote.getStatus() : null);
                return false;
            }
            return true;
        } catch (AsaasApiException ex) {
            if (ex.getAsaasStatusCode() == 404) {
                log.warn("Ignoring payment webhook; Asaas payment not found: transactionId={}", transaction.getId());
                return false;
            }
            throw ex;
        }
    }

    private boolean remoteTransferMatches(Transaction transaction, Set<String> expectedStatuses) {
        String asaasId = transaction.getAsaasPaymentId();
        if (asaasId == null || asaasId.isBlank()) {
            log.warn("Ignoring transfer webhook without Asaas id: transactionId={}", transaction.getId());
            return false;
        }
        try {
            AsaasTransferResponse remote = asaasTransferClient.retrieveTransfer(resolveApiKey(transaction), asaasId);
            if (remote == null || !statusIn(remote.getStatus(), expectedStatuses)) {
                log.warn("Ignoring transfer webhook; Asaas status incompatible: transactionId={}, remoteStatus={}",
                        transaction.getId(), remote != null ? remote.getStatus() : null);
                return false;
            }
            return true;
        } catch (AsaasApiException ex) {
            if (ex.getAsaasStatusCode() == 404) {
                log.warn("Ignoring transfer webhook; Asaas transfer not found: transactionId={}", transaction.getId());
                return false;
            }
            throw ex;
        }
    }

    private String resolveApiKey(Transaction transaction) {
        try {
            Subaccount owner = ownerSubaccount(transaction);
            if (owner != null && owner.getEncryptedApiKey() != null) {
                String key = asaasApiKeyResolver.resolveForSubaccount(owner.getId());
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Falling back to root Asaas key for webhook retrieve: {}", ex.getMessage());
        }
        return asaasProperties.getKey();
    }

    private static boolean statusIn(String status, Set<String> expected) {
        if (status == null) {
            return false;
        }
        return expected.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private UUID resolveOrganizationId(Transaction transaction) {
        if (transaction.getOrganization() != null) {
            return transaction.getOrganization().getId();
        }
        if (transaction.getAccount() != null && transaction.getAccount().getOrganization() != null) {
            return transaction.getAccount().getOrganization().getId();
        }
        return orgIdOf(transaction.getWallet());
    }

    private static UUID orgIdOf(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        if (wallet.getAccount() != null && wallet.getAccount().getOrganization() != null) {
            return wallet.getAccount().getOrganization().getId();
        }
        if (wallet.getSubaccount() != null
                && wallet.getSubaccount().getAccount() != null
                && wallet.getSubaccount().getAccount().getOrganization() != null) {
            return wallet.getSubaccount().getAccount().getOrganization().getId();
        }
        return null;
    }

    private static Map<String, Object> amountData(Transaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", transaction.getAmount().toPlainString());
        data.put("transactionId", transaction.getId().toString());
        return data;
    }
}
