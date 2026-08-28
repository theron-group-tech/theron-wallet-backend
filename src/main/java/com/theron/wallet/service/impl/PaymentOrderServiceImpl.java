package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasAccountTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.DecidePaymentOrderRequest;
import com.theron.wallet.dto.response.PaymentOrderDestinationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.PaymentOrder;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.PaymentOrderStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.AsaasApiException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.PaymentOrderMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.PaymentOrderRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountAsaasGateway;
import com.theron.wallet.service.AsaasBalanceService;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.PaymentOrderService;
import com.theron.wallet.service.TransactionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final ResourceAuthorization resourceAuthorization;
    private final AccountAsaasGateway accountAsaasGateway;
    private final AsaasBalanceService asaasBalanceService;
    private final AsaasTransferClient asaasTransferClient;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final LedgerService ledgerService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PaymentOrderResponse create(UUID actorUserId, CreatePaymentOrderRequest request) {
        resourceAuthorization.requireOrganization(actorUserId, request.getOrganizationId(), PermissionCodes.PAYMENT_ORDERS_CREATE);
        Account destination = accountRepository.findById(request.getDestinationAccountId()).orElseThrow(() -> new ResourceNotFoundException("Account", "id", request.getDestinationAccountId()));
        if (!destination.getOrganization().getId().equals(request.getOrganizationId())) throw new InvalidRequestException("Destination account does not belong to organization");
        User creator = userRepository.findById(actorUserId).orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        PaymentOrder order = PaymentOrder.builder().organization(destination.getOrganization()).destinationAccount(destination).amount(request.getAmount()).currency(request.getCurrency() == null ? "BRL" : request.getCurrency()).description(request.getDescription()).status(PaymentOrderStatus.PENDING_APPROVAL).createdBy(creator).build();
        return PaymentOrderMapper.toResponse(paymentOrderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentOrderResponse> list(UUID actorUserId, UUID organizationId, PaymentOrderStatus status, Pageable pageable) {
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_VIEW);
        if (status == null) return paymentOrderRepository.findByOrganization_Id(organizationId, pageable).map(PaymentOrderMapper::toResponse);
        return paymentOrderRepository.findByOrganization_IdAndStatus(organizationId, status, pageable).map(PaymentOrderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse get(UUID actorUserId, UUID paymentOrderId) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_VIEW);
        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse cancel(UUID actorUserId, UUID paymentOrderId) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_CREATE);
        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be cancelled");
        order.setStatus(PaymentOrderStatus.CANCELLED);
        return PaymentOrderMapper.toResponse(paymentOrderRepository.save(order));
    }

    @Override
    @Transactional
    public PaymentOrderResponse approve(UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request) {
        PaymentOrder order = requireOrder(paymentOrderId);
        UUID organizationId = order.getOrganization().getId();
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_APPROVE);
        if (order.getStatus() == PaymentOrderStatus.PROCESSING) return syncProcessingOrder(paymentOrderId);
        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be approved");
        if (order.getCreatedBy().getId().equals(actorUserId)) throw new ForbiddenException("Creator cannot approve their own payment order");
        Account source = resolveApprovingOwnerAccount(organizationId, actorUserId);
        Account destination = order.getDestinationAccount();
        User decider = userRepository.findById(actorUserId).orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        order.setSourceAccount(source);
        assertSufficientBalance(source.getId(), order.getAmount());
        accountAsaasGateway.requireConfiguredSubaccount(source.getId());
        Subaccount destinationSubaccount = accountAsaasGateway.requireConfiguredSubaccount(destination.getId());
        if (destinationSubaccount.getAsaasWalletId() == null || destinationSubaccount.getAsaasWalletId().isBlank()) throw new InvalidRequestException("Destination account has no Asaas wallet configured");
        order.setStatus(PaymentOrderStatus.PROCESSING);
        order.setDecidedBy(decider);
        order.setDecidedAt(LocalDateTime.now());
        if (request != null) order.setDecisionComment(request.getComment());
        order = paymentOrderRepository.save(order);
        try {
            AsaasTransferResponse providerTransfer = executeTransfer(order, decider, destinationSubaccount);
            order.setAsaasTransferId(providerTransfer.getId());
            if (isCompletedStatus(providerTransfer.getStatus())) {
                completeProcessingTransactions(order);
                completeLocalPaymentOrder(order, providerTransfer);
            } else if (isPendingStatus(providerTransfer.getStatus())) {
                order.setStatus(PaymentOrderStatus.PROCESSING);
                order = paymentOrderRepository.save(order);
            } else {
                throw new InvalidRequestException("Asaas transfer failed to enter a processable state (status=" + providerTransfer.getStatus() + ")");
            }
        } catch (RuntimeException ex) {
            order.setStatus(order.getAsaasTransferId() != null && !order.getAsaasTransferId().isBlank() ? PaymentOrderStatus.PROCESSING : PaymentOrderStatus.FAILED);
            paymentOrderRepository.save(order);
            throw ex;
        }
        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse reject(UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_REJECT);
        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be rejected");
        if (order.getCreatedBy().getId().equals(actorUserId)) throw new ForbiddenException("Creator cannot reject their own payment order");
        User decider = userRepository.findById(actorUserId).orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        order.setStatus(PaymentOrderStatus.REJECTED);
        order.setDecidedBy(decider);
        order.setDecidedAt(LocalDateTime.now());
        if (request != null) order.setDecisionComment(request.getComment());
        return PaymentOrderMapper.toResponse(paymentOrderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrderDestinationResponse> listDestinations(UUID actorUserId, UUID organizationId) {
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_CREATE);
        return accountRepository.findByOrganizationIdWithOwner(organizationId).stream().filter(account -> account.getOwnerUser() != null).map(account -> PaymentOrderDestinationResponse.builder().accountId(account.getId()).accountName(account.getName()).ownerUserId(account.getOwnerUser().getId()).ownerName(account.getOwnerUser().getName()).build()).toList();
    }

    @Override
    @Transactional
    public PaymentOrderResponse syncProcessingOrder(UUID paymentOrderId) {
        PaymentOrder order = requireOrder(paymentOrderId);
        if (order.getStatus() != PaymentOrderStatus.PROCESSING) throw new InvalidRequestException("Only PROCESSING payment orders can be synced");
        if (order.getSourceAccount() == null) throw new InvalidRequestException("Payment order has no source account");
        String transferId = order.getAsaasTransferId();
        if ((transferId == null || transferId.isBlank()) && order.getDebitTransaction() != null) transferId = order.getDebitTransaction().getAsaasPaymentId();
        if (transferId == null || transferId.isBlank()) throw new InvalidRequestException("Payment order has no Asaas transfer id");
        String sourceApiKey = accountAsaasGateway.resolveApiKey(order.getSourceAccount().getId());
        try {
            AsaasTransferResponse remote = asaasTransferClient.retrieveTransfer(sourceApiKey, transferId);
            order.setAsaasTransferId(remote.getId() == null ? transferId : remote.getId());
            if (isCompletedStatus(remote.getStatus())) {
                completeProcessingTransactions(order);
                completeLocalPaymentOrder(order, remote);
                return PaymentOrderMapper.toResponse(order);
            }
            if (isPendingStatus(remote.getStatus())) return PaymentOrderMapper.toResponse(paymentOrderRepository.save(order));
            if (isFailedStatus(remote.getStatus())) {
                order.setStatus(PaymentOrderStatus.FAILED);
                paymentOrderRepository.save(order);
                throw new InvalidRequestException("Asaas transfer failed (status=" + remote.getStatus() + ")");
            }
            throw new InvalidRequestException("Unknown Asaas transfer status (status=" + remote.getStatus() + ")");
        } catch (AsaasApiException ex) {
            if (ex.getAsaasStatusCode() == 404) {
                order.setStatus(PaymentOrderStatus.FAILED);
                paymentOrderRepository.save(order);
                throw new InvalidRequestException("Asaas transfer not found; payment order marked FAILED");
            }
            throw ex;
        }
    }

    private AsaasTransferResponse executeTransfer(PaymentOrder order, User actor, Subaccount destinationSubaccount) {
        UUID sourceId = order.getSourceAccount().getId();
        UUID destId = order.getDestinationAccount().getId();
        UUID firstId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
        UUID secondId = sourceId.compareTo(destId) < 0 ? destId : sourceId;
        Wallet sourceWallet = sourceId.equals(firstId) ? walletRepository.findByAccountIdWithLock(firstId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", firstId)) : walletRepository.findByAccountIdWithLock(secondId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", secondId));
        walletRepository.findByAccountIdWithLock(sourceId.equals(firstId) ? secondId : firstId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", destId));
        if (sourceWallet.getBalance().compareTo(order.getAmount()) < 0) throw new InsufficientBalanceException(String.format("Saldo no ledger local insuficiente. Disponível: %s, Solicitado: %s", sourceWallet.getBalance(), order.getAmount()));
        String idempotencyKey = order.getId().toString().replace("-", "");
        String sourceApiKey = accountAsaasGateway.resolveApiKey(sourceId);
        AsaasTransferResponse providerTransfer = confirmAsaasAccountTransfer(order, destinationSubaccount, sourceApiKey, idempotencyKey);
        order.setAsaasTransferId(providerTransfer.getId());
        if (order.getDebitTransaction() == null) {
            String requestHash = idempotencyService.hash(TransactionType.TRANSFER_OUT.name(), sourceId.toString(), destId.toString(), idempotencyService.amountPart(order.getAmount()), "PAYMENT_ORDER");
            Transaction.TransactionBuilder debitBuilder = Transaction.builder().wallet(sourceWallet).type(TransactionType.TRANSFER_OUT).status(TransactionStatus.PROCESSING).amount(order.getAmount()).description(order.getDescription() == null ? "Payment order " + order.getId() : order.getDescription()).reference("payment-order:" + order.getId()).idempotencyKey(idempotencyKey).requestHash(requestHash).createdBy(actor);
            idempotencyService.applyOwner(debitBuilder, sourceWallet);
            order.setDebitTransaction(transactionRepository.save(debitBuilder.build()));
        }
        Transaction debitTx = order.getDebitTransaction();
        debitTx.setAsaasPaymentId(providerTransfer.getId());
        debitTx.setExternalReference(providerTransfer.getId());
        transactionRepository.save(debitTx);
        if (order.getCreditTransaction() == null) {
            Wallet destWallet = walletRepository.findByAccountIdWithLock(destId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", destId));
            Transaction.TransactionBuilder creditBuilder = Transaction.builder().wallet(destWallet).type(TransactionType.TRANSFER_IN).status(TransactionStatus.PROCESSING).amount(order.getAmount()).description(order.getDescription() == null ? "Payment order " + order.getId() : order.getDescription()).reference("payment-order:" + order.getId()).externalReference(debitTx.getId().toString()).idempotencyKey(UUID.randomUUID().toString()).createdBy(actor);
            idempotencyService.applyOwner(creditBuilder, destWallet);
            order.setCreditTransaction(transactionRepository.save(creditBuilder.build()));
        }
        return paymentOrderRepository.save(order) != null ? providerTransfer : providerTransfer;
    }

    private AsaasTransferResponse confirmAsaasAccountTransfer(PaymentOrder order, Subaccount destinationSubaccount, String sourceApiKey, String idempotencyKey) {
        if (order.getAsaasTransferId() != null && !order.getAsaasTransferId().isBlank()) return asaasTransferClient.retrieveTransfer(sourceApiKey, order.getAsaasTransferId());
        AsaasAccountTransferRequest request = AsaasAccountTransferRequest.builder().value(order.getAmount()).walletId(destinationSubaccount.getAsaasWalletId()).externalReference("payment-order:" + order.getId()).build();
        AsaasTransferResponse created = asaasTransferClient.createAccountTransfer(sourceApiKey, request, idempotencyKey);
        if (created.getId() == null || created.getId().isBlank()) throw new InvalidRequestException("Asaas did not return a transfer id");
        order.setAsaasTransferId(created.getId());
        paymentOrderRepository.save(order);
        AsaasTransferResponse confirmed;
        try { confirmed = asaasTransferClient.retrieveTransfer(sourceApiKey, created.getId()); }
        catch (AsaasApiException ex) { if (ex.getAsaasStatusCode() == 404) throw new InvalidRequestException("Asaas transfer not found after creation (id=" + created.getId() + ")"); throw ex; }
        if (confirmed.getId() == null || !confirmed.getId().equals(created.getId())) throw new InvalidRequestException("Asaas transfer id mismatch after confirmation");
        if (isPendingStatus(confirmed.getStatus()) || isCompletedStatus(confirmed.getStatus())) return confirmed;
        throw new InvalidRequestException("Asaas account transfer failed (status=" + confirmed.getStatus() + ")");
    }

    private void completeProcessingTransactions(PaymentOrder order) {
        UUID sourceId = order.getSourceAccount().getId();
        UUID destId = order.getDestinationAccount().getId();
        UUID firstId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
        UUID secondId = sourceId.compareTo(destId) < 0 ? destId : sourceId;
        Wallet first = walletRepository.findByAccountIdWithLock(firstId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", firstId));
        Wallet second = walletRepository.findByAccountIdWithLock(secondId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", secondId));
        Wallet sourceWallet = sourceId.equals(firstId) ? first : second;
        Wallet destWallet = destId.equals(firstId) ? first : second;
        Transaction debitTx = order.getDebitTransaction();
        Transaction creditTx = order.getCreditTransaction();
        if (debitTx == null || creditTx == null) throw new InvalidRequestException("Payment order transactions are incomplete");
        if (debitTx.getStatus() != TransactionStatus.COMPLETED) {
            if (sourceWallet.getBalance().compareTo(order.getAmount()) < 0) throw new InsufficientBalanceException(String.format("Saldo no ledger local insuficiente. Disponível: %s, Solicitado: %s", sourceWallet.getBalance(), order.getAmount()));
            sourceWallet.setBalance(sourceWallet.getBalance().subtract(order.getAmount()));
            destWallet.setBalance(destWallet.getBalance().add(order.getAmount()));
            walletRepository.save(sourceWallet);
            walletRepository.save(destWallet);
        }
        debitTx.setStatus(TransactionStatus.COMPLETED);
        debitTx.setCompletedAt(LocalDateTime.now());
        creditTx.setStatus(TransactionStatus.COMPLETED);
        creditTx.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(debitTx);
        transactionRepository.save(creditTx);
        ledgerService.postTransfer(debitTx, creditTx, order.getAmount(), order.getCurrency(), "Payment order " + order.getId());
    }

    private void completeLocalPaymentOrder(PaymentOrder order, AsaasTransferResponse providerTransfer) {
        order.setStatus(PaymentOrderStatus.COMPLETED);
        order.setAsaasTransferId(providerTransfer.getId());
        paymentOrderRepository.save(order);
        auditLogService.record(AuditAction.PAYMENT_ORDER_APPROVED, order.getOrganization().getId(), order.getDecidedBy().getId(), "PaymentOrder", order.getId(), Map.of("asaasTransferId", providerTransfer.getId(), "status", providerTransfer.getStatus()));
    }

    private boolean isPendingStatus(String status) {
        return status != null && ("PENDING".equalsIgnoreCase(status) || "SCHEDULED".equalsIgnoreCase(status));
    }

    private boolean isCompletedStatus(String status) {
        return status != null && ("DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status));
    }

    private boolean isFailedStatus(String status) {
        return status != null && ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status));
    }

    private PaymentOrder requireOrder(UUID paymentOrderId) {
        return paymentOrderRepository.findById(paymentOrderId).orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "id", paymentOrderId));
    }

    private Account resolveApprovingOwnerAccount(UUID organizationId, UUID actorUserId) {
        return accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, actorUserId).orElseThrow(() -> new ResourceNotFoundException("Account", "organizationId/ownerUserId", organizationId + "/" + actorUserId));
    }

    private void assertSufficientBalance(UUID accountId, java.math.BigDecimal amount) {
        Wallet wallet = walletRepository.findByAccountId(accountId).orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));
        if (wallet.getBalance().compareTo(amount) < 0) throw new InsufficientBalanceException(String.format("Saldo insuficiente. Disponível: %s, Solicitado: %s", wallet.getBalance(), amount));
    }
}
