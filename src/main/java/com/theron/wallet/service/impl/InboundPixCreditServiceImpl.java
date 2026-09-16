package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.InboundPixCreditService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundPixCreditServiceImpl implements InboundPixCreditService {

    private final TransactionRepository transactionRepository;
    private final InboundWalletResolver inboundWalletResolver;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Optional<Transaction> credit(
            Subaccount destination,
            String paymentOrResourceId,
            BigDecimal amount,
            String description,
            String reference) {
        if (destination == null) {
            throw new IllegalArgumentException("destination subaccount is required");
        }
        if (paymentOrResourceId == null || paymentOrResourceId.isBlank()) {
            throw new IllegalArgumentException("paymentOrResourceId is required");
        }
        if (amount == null || amount.signum() <= 0) {
            log.warn("Ignoring inbound PIX credit without positive value: paymentId={}", paymentOrResourceId);
            return Optional.empty();
        }

        Optional<Transaction> byPaymentId = transactionRepository.findByAsaasPaymentIdForUpdate(paymentOrResourceId);
        if (byPaymentId.isPresent()) {
            return Optional.of(completeIfNeeded(byPaymentId.get()));
        }

        String idempotencyKey = InboundPixCreditService.idempotencyKey(paymentOrResourceId);
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            return Optional.of(completeIfNeeded(existing.get()));
        }

        Wallet wallet = inboundWalletResolver.resolveAndLink(destination);
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .account(destination.getAccount())
                .organization(destination.getAccount() != null ? destination.getAccount().getOrganization() : null)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.COMPLETED)
                .amount(amount)
                .currency("BRL")
                .description(description != null && !description.isBlank() ? description : "PIX recebido via Asaas")
                .reference(reference)
                .asaasPaymentId(paymentOrResourceId)
                .idempotencyKey(idempotencyKey)
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(transaction);
        walletService.credit(wallet.getId(), amount);
        notifyInboundPix(transaction);

        log.info("Inbound PIX credited: transactionId={}, walletId={}, amount={}, paymentId={}",
                transaction.getId(), wallet.getId(), amount, paymentOrResourceId);
        return Optional.of(transaction);
    }

    private Transaction completeIfNeeded(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            log.info("Inbound PIX payment already credited: paymentId={}, transactionId={}",
                    transaction.getAsaasPaymentId(), transaction.getId());
            return transaction;
        }
        if (awaitsProvider(transaction)) {
            transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
            walletService.credit(transaction.getWallet().getId(), transaction.getAmount());
            notifyInboundPix(transaction);
        }
        return transaction;
    }

    private void notifyInboundPix(Transaction transaction) {
        UUID orgId = resolveOrganizationId(transaction);
        if (orgId == null) {
            return;
        }
        notificationService.notifyUsersWithPermission(
                orgId,
                PermissionCodes.WALLET_READ,
                null,
                NotificationType.PIX_RECEIVED,
                transaction.getId(),
                amountData(transaction));
    }

    private static boolean awaitsProvider(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING
                || transaction.getStatus() == TransactionStatus.PROCESSING
                || transaction.getStatus() == TransactionStatus.PENDING_APPROVAL;
    }

    private UUID resolveOrganizationId(Transaction transaction) {
        if (transaction.getOrganization() != null) {
            return transaction.getOrganization().getId();
        }
        if (transaction.getAccount() != null && transaction.getAccount().getOrganization() != null) {
            return transaction.getAccount().getOrganization().getId();
        }
        if (transaction.getWallet() != null
                && transaction.getWallet().getAccount() != null
                && transaction.getWallet().getAccount().getOrganization() != null) {
            return transaction.getWallet().getAccount().getOrganization().getId();
        }
        return null;
    }

    private static Map<String, Object> amountData(Transaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", transaction.getAmount());
        data.put("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "BRL");
        return data;
    }
}
