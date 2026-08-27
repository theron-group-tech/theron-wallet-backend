package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasAccountTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.DecidePaymentOrderRequest;
import com.theron.wallet.dto.response.PaymentOrderDestinationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.PaymentOrder;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.enums.PaymentOrderStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.PaymentOrderMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.OrganizationRepository;
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
    private final OrganizationRepository organizationRepository;
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final ResourceAuthorization resourceAuthorization;
    private final AuditLogService auditLogService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final AsaasBalanceService asaasBalanceService;
    private final AccountAsaasGateway accountAsaasGateway;
    private final AsaasTransferClient asaasTransferClient;

    @Override
    @Transactional
    public PaymentOrderResponse create(UUID actorUserId, CreatePaymentOrderRequest request) {
        resourceAuthorization.requireOrganization(
                actorUserId, request.getOrganizationId(), PermissionCodes.PAYMENT_ORDERS_CREATE);

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization", "id", request.getOrganizationId()));

        Account source = resolveOwnerAccount(request.getOrganizationId());
        Account destination = accountRepository.findByIdWithOrganization(request.getDestinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", "id", request.getDestinationAccountId()));

        if (!destination.getOrganization().getId().equals(request.getOrganizationId())) {
            throw new ForbiddenException("Access denied");
        }
        if (source.getId().equals(destination.getId())) {
            throw new InvalidRequestException("Source and destination must be different accounts");
        }

        User creator = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.builder()
                .organization(organization)
                .sourceAccount(source)
                .destinationAccount(destination)
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(PaymentOrderStatus.PENDING_APPROVAL)
                .createdBy(creator)
                .build());

        auditLogService.record(
                AuditAction.PAYMENT_ORDER_CREATED,
                organization.getId(),
                actorUserId,
                "PaymentOrder",
                order.getId(),
                Map.of(
                        "amount", request.getAmount().toPlainString(),
                        "destinationAccountId", destination.getId().toString(),
                        "sourceAccountId", source.getId().toString()));

        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentOrderResponse> list(
            UUID actorUserId, UUID organizationId, PaymentOrderStatus status, Pageable pageable) {
        resourceAuthorization.requireOrganization(actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_READ);
        Page<PaymentOrder> page = status == null
                ? paymentOrderRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId, pageable)
                : paymentOrderRepository.findByOrganization_IdAndStatusOrderByCreatedAtDesc(
                        organizationId, status, pageable);
        return page.map(PaymentOrderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse get(UUID actorUserId, UUID paymentOrderId) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(
                actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_READ);
        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse cancel(UUID actorUserId, UUID paymentOrderId) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(
                actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_CANCEL);

        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) {
            throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be cancelled");
        }

        order.setStatus(PaymentOrderStatus.CANCELLED);
        order = paymentOrderRepository.save(order);

        auditLogService.record(
                AuditAction.PAYMENT_ORDER_CANCELLED,
                order.getOrganization().getId(),
                actorUserId,
                "PaymentOrder",
                order.getId(),
                Map.of());

        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse approve(
            UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request) {
        PaymentOrder order = requireOrder(paymentOrderId);
        UUID organizationId = order.getOrganization().getId();
        resourceAuthorization.requireOrganization(
                actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_APPROVE);

        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) {
            throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be approved");
        }
        if (order.getCreatedBy().getId().equals(actorUserId)) {
            throw new ForbiddenException("Creator cannot approve their own payment order");
        }

        User decider = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

        Account source = resolveApprovingOwnerAccount(organizationId, actorUserId);
        Account destination = order.getDestinationAccount();
        if (source.getId().equals(destination.getId())) {
            throw new InvalidRequestException("Approving OWNER account cannot be the destination account");
        }

        order.setSourceAccount(source);
        assertSufficientBalance(source.getId(), order.getAmount());

        // Validate both Asaas rails before changing the local financial state.
        accountAsaasGateway.requireConfiguredSubaccount(source.getId());
        Subaccount destinationSubaccount = accountAsaasGateway.requireConfiguredSubaccount(destination.getId());
        if (destinationSubaccount.getAsaasWalletId() == null || destinationSubaccount.getAsaasWalletId().isBlank()) {
            throw new InvalidRequestException("Destination account has no Asaas wallet configured");
        }

        order.setStatus(PaymentOrderStatus.PROCESSING);
        order.setDecidedBy(decider);
        order.setDecidedAt(LocalDateTime.now());
        if (request != null) {
            order.setDecisionComment(request.getComment());
        }
        order = paymentOrderRepository.save(order);

        try {
            AsaasTransferResponse providerTransfer = executeTransfer(order, decider, destinationSubaccount);
            String providerStatus = providerTransfer.getStatus();

            if ("CANCELLED".equalsIgnoreCase(providerStatus)) {
                throw new InvalidRequestException("Asaas cancelled the payment order transfer");
            }

            if ("DONE".equalsIgnoreCase(providerStatus) || providerStatus == null || providerStatus.isBlank()) {
                order.setStatus(PaymentOrderStatus.COMPLETED);
                order.setCompletedAt(LocalDateTime.now());
            } else {
                // Asaas accepted the transfer but is still processing it.
                order.setStatus(PaymentOrderStatus.PROCESSING);
            }

            order = paymentOrderRepository.save(order);

            auditLogService.record(
                    AuditAction.PAYMENT_ORDER_APPROVED,
                    organizationId,
                    actorUserId,
                    "PaymentOrder",
                    order.getId(),
                    Map.of(
                            "status", order.getStatus().name(),
                            "sourceAccountId", source.getId().toString(),
                            "destinationAccountId", destination.getId().toString(),
                            "asaasTransferId", providerTransfer.getId() == null ? "unknown" : providerTransfer.getId(),
                            "asaasTransferStatus", providerStatus == null ? "unknown" : providerStatus,
                            "approvingOwnerUserId", actorUserId.toString()));

            if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
                auditLogService.record(
                        AuditAction.PAYMENT_ORDER_COMPLETED,
                        organizationId,
                        actorUserId,
                        "PaymentOrder",
                        order.getId(),
                        Map.of("asaasTransferId", providerTransfer.getId() == null ? "unknown" : providerTransfer.getId()));
            }
        } catch (RuntimeException ex) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order = paymentOrderRepository.save(order);
            auditLogService.record(
                    AuditAction.PAYMENT_ORDER_FAILED,
                    organizationId,
                    actorUserId,
                    "PaymentOrder",
                    order.getId(),
                    Map.of("error", ex.getMessage() == null ? "failed" : ex.getMessage()));
            throw ex;
        }

        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public PaymentOrderResponse reject(
            UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request) {
        PaymentOrder order = requireOrder(paymentOrderId);
        resourceAuthorization.requireOrganization(
                actorUserId, order.getOrganization().getId(), PermissionCodes.PAYMENT_ORDERS_REJECT);

        if (order.getStatus() != PaymentOrderStatus.PENDING_APPROVAL) {
            throw new InvalidRequestException("Only PENDING_APPROVAL payment orders can be rejected");
        }
        if (order.getCreatedBy().getId().equals(actorUserId)) {
            throw new ForbiddenException("Creator cannot reject their own payment order");
        }

        User decider = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

        order.setStatus(PaymentOrderStatus.REJECTED);
        order.setDecidedBy(decider);
        order.setDecidedAt(LocalDateTime.now());
        if (request != null) {
            order.setDecisionComment(request.getComment());
        }
        order = paymentOrderRepository.save(order);

        auditLogService.record(
                AuditAction.PAYMENT_ORDER_REJECTED,
                order.getOrganization().getId(),
                actorUserId,
                "PaymentOrder",
                order.getId(),
                Map.of());

        return PaymentOrderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrderDestinationResponse> listDestinations(UUID actorUserId, UUID organizationId) {
        resourceAuthorization.requireOrganization(
                actorUserId, organizationId, PermissionCodes.PAYMENT_ORDERS_CREATE);

        return accountRepository.findByOrganizationIdWithOwner(organizationId).stream()
                .filter(account -> account.getOwnerUser() != null)
                .map(account -> PaymentOrderDestinationResponse.builder()
                        .accountId(account.getId())
                        .accountName(account.getName())
                        .ownerUserId(account.getOwnerUser().getId())
                        .ownerName(account.getOwnerUser().getName())
                        .build())
                .toList();
    }

    private AsaasTransferResponse executeTransfer(
            PaymentOrder order,
            User actor,
            Subaccount destinationSubaccount) {
        UUID sourceId = order.getSourceAccount().getId();
        UUID destId = order.getDestinationAccount().getId();
        boolean sourceFirst = sourceId.compareTo(destId) < 0;
        UUID firstId = sourceFirst ? sourceId : destId;
        UUID secondId = sourceFirst ? destId : sourceId;

        Wallet first = walletRepository.findByAccountIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", firstId));
        Wallet second = walletRepository.findByAccountIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", secondId));

        Wallet sourceWallet = sourceFirst ? first : second;
        Wallet destWallet = sourceFirst ? second : first;

        if (sourceWallet.getBalance().compareTo(order.getAmount()) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Saldo no ledger local insuficiente. Disponível: %s, Solicitado: %s",
                    sourceWallet.getBalance(), order.getAmount()));
        }

        // The local ledger is changed before the provider call. If Asaas rejects the
        // transfer, the surrounding transaction rolls the local changes back. If the
        // provider times out after accepting the transfer, the same idempotency key is
        // reused on retry to prevent a duplicate movement.
        sourceWallet.debit(order.getAmount());
        destWallet.credit(order.getAmount());
        walletRepository.save(sourceWallet);
        walletRepository.save(destWallet);

        String idempotencyKey = order.getId().toString().replace("-", "");
        String requestHash = idempotencyService.hash(
                TransactionType.TRANSFER_OUT.name(),
                sourceId.toString(),
                destId.toString(),
                idempotencyService.amountPart(order.getAmount()),
                "PAYMENT_ORDER");

        Transaction.TransactionBuilder debitBuilder = Transaction.builder()
                .wallet(sourceWallet)
                .type(TransactionType.TRANSFER_OUT)
                .status(TransactionStatus.PROCESSING)
                .amount(order.getAmount())
                .description(order.getDescription() == null
                        ? "Payment order " + order.getId()
                        : order.getDescription())
                .reference("payment-order:" + order.getId())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .createdBy(actor);
        idempotencyService.applyOwner(debitBuilder, sourceWallet);
        Transaction debitTx = transactionRepository.save(debitBuilder.build());
        debitTx = transactionLifecycleService.transition(debitTx, TransactionStatus.COMPLETED);

        Transaction.TransactionBuilder creditBuilder = Transaction.builder()
                .wallet(destWallet)
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.PROCESSING)
                .amount(order.getAmount())
                .description(order.getDescription() == null
                        ? "Payment order " + order.getId()
                        : order.getDescription())
                .reference("payment-order:" + order.getId())
                .externalReference(debitTx.getId().toString())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdBy(actor);
        idempotencyService.applyOwner(creditBuilder, destWallet);
        Transaction creditTx = transactionRepository.save(creditBuilder.build());
        creditTx = transactionLifecycleService.transition(creditTx, TransactionStatus.COMPLETED);

        ledgerService.postTransfer(
                sourceId,
                destId,
                order.getAmount(),
                "ledger:payment-order:" + order.getId(),
                debitTx.getId().toString());

        // Payment Orders move funds between Theron Accounts that are represented by
        // Asaas subaccounts. Asaas has a dedicated account-to-account transfer flow:
        // the destination is the Asaas walletId, not the Theron Account UUID and not
        // a PIX key. This keeps the real provider balances aligned with the local ledger.
        String sourceApiKey = accountAsaasGateway.resolveApiKey(sourceId);
        AsaasAccountTransferRequest providerRequest = AsaasAccountTransferRequest.builder()
                .value(order.getAmount())
                .walletId(destinationSubaccount.getAsaasWalletId())
                .build();

        AsaasTransferResponse providerTransfer = asaasTransferClient.createAccountTransfer(
                sourceApiKey,
                providerRequest,
                idempotencyKey);

        if (providerTransfer.getId() == null || providerTransfer.getId().isBlank()) {
            throw new InvalidRequestException("Asaas did not return a transfer id");
        }

        debitTx.setAsaasPaymentId(providerTransfer.getId());
        debitTx.setExternalReference(providerTransfer.getId());
        transactionRepository.save(debitTx);

        order.setDebitTransaction(debitTx);
        order.setCreditTransaction(creditTx);

        log.info("PaymentOrder {} submitted to Asaas: debit={}, credit={}, amount={}, sourceAccountId={}, destinationAccountId={}, asaasTransferId={}, asaasStatus={}, approvingOwnerUserId={}",
                order.getId(),
                debitTx.getId(),
                creditTx.getId(),
                order.getAmount(),
                sourceId,
                destId,
                providerTransfer.getId(),
                providerTransfer.getStatus(),
                actor.getId());

        return providerTransfer;
    }

    private Account resolveOwnerAccount(UUID organizationId) {
        UUID ownerUserId = membershipRoleRepository.findOwnerUserId(organizationId)
                .orElseThrow(() -> new InvalidRequestException("Organization has no OWNER"));
        return accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, ownerUserId)
                .orElseThrow(() -> new InvalidRequestException("OWNER account not found"));
    }

    private Account resolveApprovingOwnerAccount(UUID organizationId, UUID approvingUserId) {
        boolean isOwner = membershipRoleRepository.findOwnerUserIds(organizationId).stream()
                .anyMatch(ownerUserId -> ownerUserId.equals(approvingUserId));
        if (!isOwner) {
            throw new ForbiddenException("Only an OWNER can approve payment orders");
        }
        return accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, approvingUserId)
                .orElseThrow(() -> new InvalidRequestException("Approving OWNER account not found"));
    }

    private void assertSufficientBalance(UUID accountId, java.math.BigDecimal amount) {
        Wallet wallet = walletRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));
        java.math.BigDecimal available = asaasBalanceService.displayBalance(accountId);
        if (available.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Saldo insuficiente. Disponível: %s, Solicitado: %s",
                    available, amount));
        }
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(String.format(
                    "Saldo no ledger local insuficiente (Asaas disponível: %s, ledger: %s, solicitado: %s).",
                    available, wallet.getBalance(), amount));
        }
    }

    private PaymentOrder requireOrder(UUID paymentOrderId) {
        return paymentOrderRepository.findByIdWithDetails(paymentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "id", paymentOrderId));
    }
}
