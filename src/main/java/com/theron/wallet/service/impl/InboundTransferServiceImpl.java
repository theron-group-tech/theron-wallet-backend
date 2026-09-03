package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AsaasWebhookEventStatus;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.InboundTransferService;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundTransferServiceImpl implements InboundTransferService {

    private final AsaasProperties asaasProperties;
    private final SubaccountRepository subaccountRepository;
    private final PlatformPixTransferRepository platformPixTransferRepository;
    private final TransactionRepository transactionRepository;
    private final InboundWalletResolver inboundWalletResolver;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final NotificationService notificationService;
    private final AsaasWebhookEventPersister eventPersister;

    @Override
    @Transactional(readOnly = true)
    public boolean canHandle(String token, AsaasWebhookPayload payload) {
        if (payload == null || payload.getTransfer() == null || payload.getTransfer().getId() == null
                || payload.getAccount() == null || payload.getAccount().getId() == null) {
            return false;
        }
        Subaccount subaccount = subaccountRepository.findByAsaasAccountId(payload.getAccount().getId()).orElse(null);
        if (subaccount == null) {
            return false;
        }
        return tokenMatches(token, subaccount);
    }

    @Override
    @Transactional
    public void receive(String token, AsaasWebhookPayload payload) {
        Subaccount subaccount = subaccountRepository.findByAsaasAccountId(
                        payload.getAccount() != null ? payload.getAccount().getId() : null)
                .orElseThrow(() -> new UnauthorizedException("Unknown Asaas subaccount"));

        if (!tokenMatches(token, subaccount)) {
            throw new UnauthorizedException("Invalid webhook token for Asaas subaccount");
        }

        String eventId = WebhookServiceImpl.resolveEventId(payload);
        var stored = eventPersister.claim(eventId, payload);
        if (stored.isEmpty()) {
            log.info("Duplicate inbound Asaas transfer webhook ignored: eventId={}", eventId);
            return;
        }

        try {
            processTransfer(subaccount, payload);
            eventPersister.mark(stored.get(), AsaasWebhookEventStatus.PROCESSED);
        } catch (RuntimeException ex) {
            eventPersister.mark(stored.get(), AsaasWebhookEventStatus.FAILED);
            throw ex;
        }
    }

    private void processTransfer(Subaccount subaccount, AsaasWebhookPayload payload) {
        AsaasWebhookPayload.Transfer transfer = payload.getTransfer();
        String event = payload.getEvent();
        BigDecimal amount = transfer.getValue() != null ? transfer.getValue() : transfer.getNetValue();
        if (amount == null || amount.signum() <= 0) {
            log.warn("Ignoring inbound Asaas transfer without positive value: transferId={}", transfer.getId());
            return;
        }
        if (isAlreadyHandledPlatformTransfer(transfer)) {
            return;
        }

        Optional<Transaction> existing = transactionRepository.findByAsaasPaymentIdForUpdate(transfer.getId());
        Transaction transaction = existing.orElseGet(() -> createTransaction(subaccount, transfer, amount, event));

        if ("TRANSFER_DONE".equals(event)) {
            completeInbound(transaction);
        } else if ("TRANSFER_FAILED".equals(event)
                || "TRANSFER_CANCELLED".equals(event)
                || "TRANSFER_BLOCKED".equals(event)) {
            failInbound(transaction, event);
        } else if (transaction.getStatus() == TransactionStatus.PENDING) {
            log.info("Inbound Asaas transfer pending: transferId={}, event={}", transfer.getId(), event);
        }
    }

    private boolean isAlreadyHandledPlatformTransfer(AsaasWebhookPayload.Transfer transfer) {
        if (transfer.getExternalReference() == null || transfer.getExternalReference().isBlank()) {
            return false;
        }
        return platformPixTransferRepository.findByIdempotencyKey(transfer.getExternalReference())
                .map(row -> {
                    if (row.getCreditTransactionId() != null) {
                        log.info(
                                "Inbound Platform PIX transfer already credited; webhook ignored: "
                                        + "transferId={}, transactionId={}",
                                transfer.getId(), row.getCreditTransactionId());
                        return true;
                    }
                    if (transactionRepository.findByAsaasPaymentId(transfer.getId()).isPresent()) {
                        log.info(
                                "Inbound Platform PIX transfer already has a local transaction; webhook ignored: "
                                        + "transferId={}",
                                transfer.getId());
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    private Transaction createTransaction(
            Subaccount subaccount,
            AsaasWebhookPayload.Transfer transfer,
            BigDecimal amount,
            String event) {
        Wallet wallet = inboundWalletResolver.resolveAndLink(subaccount);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .account(subaccount.getAccount())
                .organization(subaccount.getAccount() != null ? subaccount.getAccount().getOrganization() : null)
                .type(TransactionType.TRANSFER_IN)
                .status("TRANSFER_DONE".equals(event) ? TransactionStatus.COMPLETED : TransactionStatus.PENDING)
                .amount(amount)
                .currency("BRL")
                .description(transfer.getDescription() != null ? transfer.getDescription() : "Transferência recebida via Asaas")
                .reference(transfer.getEffectiveDate())
                .asaasPaymentId(transfer.getId())
                .externalReference(transfer.getExternalReference())
                .idempotencyKey("asaas:transfer:in:" + transfer.getId())
                .build();

        transaction = transactionRepository.save(transaction);
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            walletService.credit(wallet.getId(), amount);
            notifyReceived(transaction);
        }
        return transaction;
    }

    private void completeInbound(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        if (transaction.getStatus() == TransactionStatus.FAILED
                || transaction.getStatus() == TransactionStatus.CANCELLED
                || transaction.getStatus() == TransactionStatus.REVERSED) {
            log.warn("Ignoring completion after terminal inbound status: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return;
        }
        transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());
        notifyReceived(transaction);
    }

    private void failInbound(Transaction transaction, String event) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED
                || transaction.getStatus() == TransactionStatus.CANCELLED) {
            return;
        }
        TransactionStatus status = "TRANSFER_CANCELLED".equals(event)
                ? TransactionStatus.CANCELLED : TransactionStatus.FAILED;
        transactionLifecycleService.transition(transaction, status);
        log.info("Inbound Asaas transfer {}: transactionId={}, event={}",
                status.name().toLowerCase(), transaction.getId(), event);
    }

    private void notifyReceived(Transaction transaction) {
        UUID organizationId = transaction.getOrganization() != null
                ? transaction.getOrganization().getId()
                : transaction.getAccount().getOrganization().getId();
        notificationService.notifyUsersWithPermission(
                organizationId,
                com.theron.wallet.security.PermissionCodes.WALLET_READ,
                null,
                NotificationType.TRANSFER_RECEIVED,
                transaction.getId(),
                amountData(transaction));
    }

    private static Map<String, Object> amountData(Transaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", transaction.getAmount().toPlainString());
        data.put("transactionId", transaction.getId().toString());
        data.put("type", transaction.getType().name());
        return data;
    }

    private boolean tokenMatches(String token, Subaccount subaccount) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String expected = subaccount.getWebhookToken();
        if (expected == null || expected.isBlank()) {
            expected = asaasProperties.getWebhookToken();
        }
        return expected != null && !expected.isBlank()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
