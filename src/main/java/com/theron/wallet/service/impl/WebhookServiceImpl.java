package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.AsaasPaymentEvent;
import com.theron.wallet.enums.AsaasTransferEvent;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.WalletService;
import com.theron.wallet.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;

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

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }

        if (event.isConfirmation()) {
            handlePaymentConfirmation(transaction);
        } else if (event.isCancellation()) {
            handlePaymentCancellation(transaction, event);
        } else {
            log.info("Webhook event {} does not require action for transactionId={}",
                    eventName, transaction.getId());
        }
    }

    private void handlePaymentConfirmation(Transaction transaction) {
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transactionRepository.save(transaction);

        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());

        log.info("Deposit confirmed: transactionId={}, walletId={}, amount={}",
                transaction.getId(), transaction.getWallet().getId(), transaction.getAmount());
    }

    private void handlePaymentCancellation(Transaction transaction, AsaasPaymentEvent event) {
        TransactionStatus newStatus = (event == AsaasPaymentEvent.PAYMENT_DELETED)
                ? TransactionStatus.CANCELLED
                : TransactionStatus.FAILED;

        transaction.setStatus(newStatus);
        transactionRepository.save(transaction);

        log.info("Deposit {}: transactionId={}, event={}",
                newStatus.name().toLowerCase(), transaction.getId(), event.getValue());
    }

    // --- Transfer webhook handling ---

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

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Transaction already in terminal state: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }

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
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transactionRepository.save(transaction);

        log.info("Withdrawal confirmed: transactionId={}, walletId={}, amount={}",
                transaction.getId(), transaction.getWallet().getId(), transaction.getAmount());
    }

    private void handleTransferFailure(Transaction transaction, AsaasTransferEvent event) {
        TransactionStatus newStatus = (event == AsaasTransferEvent.TRANSFER_CANCELLED)
                ? TransactionStatus.CANCELLED
                : TransactionStatus.FAILED;

        transaction.setStatus(newStatus);
        transactionRepository.save(transaction);

        // Credit wallet back — the withdrawal debit is being reversed
        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());

        log.info("Withdrawal {}: transactionId={}, event={}, amount credited back to walletId={}",
                newStatus.name().toLowerCase(), transaction.getId(), event.getValue(),
                transaction.getWallet().getId());
    }
}
