package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.PixTransaction;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AsaasPaymentEvent;
import com.theron.wallet.enums.AsaasTransferEvent;
import com.theron.wallet.enums.AsaasWebhookEventStatus;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import com.theron.wallet.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final TransactionRepository transactionRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final NotificationService notificationService;
    private final AsaasProperties asaasProperties;
    private final SubaccountRepository subaccountRepository;
    private final AsaasWebhookEventPersister asaasWebhookEventPersister;

    @Override
    @Transactional
    public void receive(String token, AsaasWebhookPayload payload) {
        if (!isValidWebhookToken(token)) {
            throw new UnauthorizedException("Invalid webhook token");
        }

        String eventId = resolveEventId(payload);
        var stored = asaasWebhookEventPersister.claim(eventId, payload);
        if (stored.isEmpty()) {
            log.info("Duplicate Asaas webhook ignored: eventId={}", eventId);
            return;
        }

        try {
            if (payload.getEvent() != null && payload.getEvent().startsWith("TRANSFER_")) {
                processTransferWebhook(payload);
            } else {
                processPaymentWebhook(payload);
            }
            asaasWebhookEventPersister.mark(stored.get(), AsaasWebhookEventStatus.PROCESSED);
        } catch (RuntimeException ex) {
            asaasWebhookEventPersister.mark(stored.get(), AsaasWebhookEventStatus.FAILED);
            throw ex;
        }
    }

    private boolean isValidWebhookToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String expectedToken = asaasProperties.getWebhookToken();
        if (expectedToken != null && !expectedToken.isBlank()
                && MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        return subaccountRepository.findByWebhookToken(token).isPresent();
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
            log.warn("No transaction found for asaasPaymentId={}, ignoring webhook", payment.getId());
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

    private void handlePaymentConfirmation(Transaction transaction) {
        if (!awaitsProvider(transaction)) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
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
        } else if (event.isFailure()) {
            handleTransferFailure(transaction, event);
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

    private static boolean awaitsProvider(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING
                || transaction.getStatus() == TransactionStatus.PROCESSING;
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
