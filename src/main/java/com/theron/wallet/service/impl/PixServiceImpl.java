package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateAccountPixQrCodeRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.response.AccountPixKeyResponse;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Beneficiary;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.PixTransaction;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.PixMapper;
import com.theron.wallet.repository.BeneficiaryRepository;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AccountLimitService;
import com.theron.wallet.service.AccountAsaasGateway;
import com.theron.wallet.service.ApprovalPolicyService;
import com.theron.wallet.service.ApprovalWorkflowService;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.LimitContext;
import com.theron.wallet.service.PixService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.TransactionLimitService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PixServiceImpl implements PixService {

    private final AuthorizationService authorizationService;
    private final AccountAsaasGateway accountAsaasGateway;
    private final AccountLimitService accountLimitService;
    private final TransactionLimitService transactionLimitService;
    private final ApprovalPolicyService approvalPolicyService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final PixKeyRepository pixKeyRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AsaasPixClient asaasPixClient;
    private final AsaasTransferClient asaasTransferClient;
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public AccountPixKeyResponse createKey(UUID actorUserId, CreateAccountPixKeyRequest request) {
        Subaccount subaccount = accountAsaasGateway.requireConfiguredSubaccount(request.getAccountId());
        Account account = subaccount.getAccount();
        if (account == null) {
            account = loadAccountFromSubaccount(subaccount, request.getAccountId());
        }
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.PIX_CREATE);

        String apiKey = accountAsaasGateway.resolveApiKey(request.getAccountId());
        AsaasPixKeyResponse asaasResponse = asaasPixClient.createPixKey(apiKey,
                AsaasPixKeyRequest.builder().type(request.getType().name()).build());

        if (asaasResponse.getKey() != null
                && pixKeyRepository.existsByAccount_IdAndKeyAndStatus(
                        request.getAccountId(), asaasResponse.getKey(), PixKeyStatus.ACTIVE)) {
            throw new DuplicateResourceException("PixKey", "key", asaasResponse.getKey());
        }

        PixKey pixKey = PixKey.builder()
                .account(account)
                .organization(account.getOrganization())
                .type(request.getType())
                .key(asaasResponse.getKey())
                .status(PixKeyStatus.ACTIVE)
                .providerKeyId(asaasResponse.getId())
                .build();
        try {
            pixKey = pixKeyRepository.saveAndFlush(pixKey);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("PixKey already exists for this Account");
        }

        log.info("PIX key created: pixKeyId={}, accountId={}", pixKey.getId(), account.getId());
        return PixMapper.toKeyResponse(pixKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPixKeyResponse> listKeys(UUID actorUserId, UUID accountId) {
        Subaccount subaccount = accountAsaasGateway.requireConfiguredSubaccount(accountId);
        Account account = requireAccount(subaccount, accountId);
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.PIX_READ);
        return pixKeyRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(PixMapper::toKeyResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteKey(UUID actorUserId, UUID pixKeyId) {
        PixKey pixKey = pixKeyRepository.findByIdWithOwner(pixKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("PixKey", "id", pixKeyId));
        authorizationService.requirePermission(
                pixKey.getOrganization().getId(), actorUserId, PermissionCodes.PIX_CREATE);
        accountAsaasGateway.requireConfiguredSubaccount(pixKey.getAccount().getId());

        if (pixKey.getStatus() == PixKeyStatus.INACTIVE) {
            return;
        }

        if (pixKey.getProviderKeyId() != null) {
            String apiKey = accountAsaasGateway.resolveApiKey(pixKey.getAccount().getId());
            asaasPixClient.deletePixKey(apiKey, pixKey.getProviderKeyId());
        }
        pixKey.setStatus(PixKeyStatus.INACTIVE);
        pixKeyRepository.save(pixKey);
        log.info("PIX key inactivated: pixKeyId={}", pixKeyId);
    }

    @Override
    public PixTransferResponse createTransfer(UUID actorUserId, CreatePixTransferRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for PIX transfers");
        }

        Subaccount configured = accountAsaasGateway.requireConfiguredSubaccount(request.getAccountId());
        Account account = requireAccount(configured, request.getAccountId());
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.PIX_TRANSFER);

        String idempotencyKey = idempotencyService.resolveKey(null, request.getIdempotencyKey());
        TransferDestination destination = resolveDestination(request, account);
        String requestHash = transferHash(request, destination);

        return idempotencyService.findExisting(idempotencyKey, requestHash)
                .map(existing -> resumeTransferProvider(existing, request.getAccountId()))
                .orElseGet(() -> createNewTransfer(
                        actorUserId, request, account, destination, idempotencyKey, requestHash));
    }

    @Override
    public PixTransferResponse executeApprovedTransfer(UUID transactionId) {
        Transaction processing = debitHeldTransfer(transactionId);
        PixTransaction pixTransaction = pixTransactionRepository.findByTransactionId(processing.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PixTransaction", "transactionId", processing.getId()));
        return resumeTransferProvider(processing, pixTransaction.getAccount().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PixTransferResponse getTransfer(UUID actorUserId, UUID pixTransactionId) {
        PixTransaction pixTransaction = pixTransactionRepository.findByIdWithDetails(pixTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("PixTransaction", "id", pixTransactionId));
        authorizationService.requirePermission(
                pixTransaction.getAccount().getOrganization().getId(),
                actorUserId,
                PermissionCodes.PIX_READ);
        return PixMapper.toTransferResponse(pixTransaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PixTransferResponse> listTransfers(UUID actorUserId, UUID accountId, Pageable pageable) {
        Subaccount subaccount = accountAsaasGateway.requireConfiguredSubaccount(accountId);
        Account account = requireAccount(subaccount, accountId);
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.PIX_READ);
        return pixTransactionRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, pageable)
                .map(pt -> {
                    // ensure transaction loaded
                    pt.getTransaction().getAmount();
                    return PixMapper.toTransferResponse(pt);
                });
    }

    @Override
    @Transactional
    public AccountPixQrCodeResponse createQrCode(UUID actorUserId, CreateAccountPixQrCodeRequest request) {
        Subaccount subaccount = accountAsaasGateway.requireConfiguredSubaccount(request.getAccountId());
        Account account = requireAccount(subaccount, request.getAccountId());
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.PIX_CREATE);

        PixKey pixKey = pixKeyRepository.findByIdWithOwner(request.getPixKeyId())
                .orElseThrow(() -> new ResourceNotFoundException("PixKey", "id", request.getPixKeyId()));
        if (!pixKey.getAccount().getId().equals(request.getAccountId())) {
            throw new ForbiddenException("PixKey does not belong to the Account");
        }
        if (pixKey.getStatus() != PixKeyStatus.ACTIVE) {
            throw new InvalidRequestException("PixKey is not ACTIVE");
        }
        if (pixKey.getProviderKeyId() == null) {
            throw new InvalidRequestException("PixKey has no provider reference");
        }

        String apiKey = accountAsaasGateway.resolveApiKey(request.getAccountId());
        AsaasPixStaticQrCodeResponse asaasResponse = asaasPixClient.createStaticQrCode(
                apiKey,
                pixKey.getProviderKeyId(),
                AsaasPixStaticQrCodeRequest.builder()
                        .value(request.getValue())
                        .description(request.getDescription())
                        .format("ALL")
                        .build());

        return AccountPixQrCodeResponse.builder()
                .pixKeyId(pixKey.getId())
                .payload(asaasResponse.getPayload())
                .encodedImage(asaasResponse.getEncodedImage())
                .expirationDate(asaasResponse.getExpirationDate())
                .value(asaasResponse.getValue())
                .description(asaasResponse.getDescription())
                .build();
    }

    private PixTransferResponse createNewTransfer(
            UUID actorUserId,
            CreatePixTransferRequest request,
            Account account,
            TransferDestination destination,
            String idempotencyKey,
            String requestHash) {
        int requiredApprovals = approvalPolicyService.resolveRequiredApprovals(
                account.getId(), request.getAmount());

        Transaction persisted;
        try {
            if (requiredApprovals == 0) {
                persisted = persistTransfer(actorUserId, request, account, destination, idempotencyKey, requestHash);
            } else {
                Transaction held = persistPendingApproval(
                        actorUserId, request, account, destination, idempotencyKey, requestHash, requiredApprovals);
                UUID heldId = held.getId();
                PixTransaction pixTransaction = pixTransactionRepository.findByTransactionId(heldId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "PixTransaction", "transactionId", heldId));
                return PixMapper.toTransferResponse(pixTransaction);
            }
        } catch (RuntimeException ex) {
            if (!ProviderCall.isUniqueConstraint(ex)) {
                throw ex;
            }
            persisted = idempotencyService.requireExisting(idempotencyKey, requestHash);
        }
        return resumeTransferProvider(persisted, request.getAccountId());
    }

    private Transaction persistPendingApproval(
            UUID actorUserId,
            CreatePixTransferRequest request,
            Account account,
            TransferDestination destination,
            String idempotencyKey,
            String requestHash,
            int requiredApprovals) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Account managedAccount = accountRepository.findByIdWithOrganization(account.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", account.getId()));

            accountLimitService.assertWithinLimits(managedAccount.getId(), request.getAmount());
            assertHierarchicalPixLimits(managedAccount, actorUserId, request.getAmount(), null);

            Wallet wallet = walletRepository.findByAccountIdWithLock(managedAccount.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", managedAccount.getId()));

            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(String.format(
                        "Insufficient balance. Available: %s, Requested: %s",
                        wallet.getBalance(), request.getAmount()));
            }

            accountAsaasGateway.requireConfiguredSubaccount(managedAccount.getId());

            User requester = userRepository.findById(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

            Transaction.TransactionBuilder builder = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.PIX)
                    .status(TransactionStatus.PENDING_APPROVAL)
                    .amount(request.getAmount())
                    .description(request.getDescription())
                    .reference(ProviderCall.referenceOf(request.getDescription()))
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .beneficiary(destination.beneficiary())
                    .createdBy(requester);
            idempotencyService.applyOwner(builder, wallet);
            Transaction transaction = transactionRepository.save(builder.build());

            pixTransactionRepository.save(PixTransaction.builder()
                    .transaction(transaction)
                    .account(managedAccount)
                    .destinationPixKey(destination.pixKey())
                    .destinationPixKeyType(destination.pixKeyType())
                    .beneficiary(destination.beneficiary())
                    .status(TransactionStatus.PENDING_APPROVAL)
                    .build());

            approvalWorkflowService.createPendingRequest(transaction, requester, requiredApprovals);
            log.info("PIX transfer held for approval: transactionId={}, requiredApprovals={}",
                    transaction.getId(), requiredApprovals);
            return transaction;
        });
    }

    private Transaction debitHeldTransfer(UUID transactionId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
            if (locked.getStatus() != TransactionStatus.PENDING_APPROVAL) {
                throw new InvalidRequestException(
                        "Transaction is not PENDING_APPROVAL: " + locked.getStatus());
            }

            Account managedAccount = locked.getAccount();
            UUID accountId;
            if (managedAccount != null) {
                accountId = managedAccount.getId();
            } else {
                accountId = locked.getWallet().getAccount().getId();
            }

            accountLimitService.assertWithinLimits(accountId, locked.getAmount(), transactionId);
            Account accountWithOrg = accountRepository.findByIdWithOrganization(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
            UUID actorId = locked.getCreatedBy() != null ? locked.getCreatedBy().getId() : null;
            if (actorId != null) {
                assertHierarchicalPixLimits(accountWithOrg, actorId, locked.getAmount(), transactionId);
            }

            Wallet wallet = walletRepository.findByAccountIdWithLock(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", accountId));
            if (wallet.getBalance().compareTo(locked.getAmount()) < 0) {
                throw new InsufficientBalanceException(String.format(
                        "Insufficient balance. Available: %s, Requested: %s",
                        wallet.getBalance(), locked.getAmount()));
            }

            accountAsaasGateway.requireConfiguredSubaccount(accountId);

            wallet.debit(locked.getAmount());
            walletRepository.save(wallet);

            transactionLifecycleService.transition(locked, TransactionStatus.PROCESSING);
            pixTransactionRepository.findByTransactionIdForUpdate(transactionId).ifPresent(pt -> {
                pt.setStatus(TransactionStatus.PROCESSING);
                pixTransactionRepository.save(pt);
            });

            ledgerService.postDebit(
                    accountId,
                    locked.getAmount(),
                    "ledger:pix:" + locked.getIdempotencyKey(),
                    locked.getId().toString());

            return locked;
        });
    }

    private Transaction persistTransfer(
            UUID actorUserId,
            CreatePixTransferRequest request,
            Account account,
            TransferDestination destination,
            String idempotencyKey,
            String requestHash) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Account managedAccount = accountRepository.findByIdWithOrganization(account.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", account.getId()));

            // Limits before Asaas — lock limit row + count daily spend
            accountLimitService.assertWithinLimits(managedAccount.getId(), request.getAmount());
            assertHierarchicalPixLimits(managedAccount, actorUserId, request.getAmount(), null);

            Wallet wallet = walletRepository.findByAccountIdWithLock(managedAccount.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet", "accountId", managedAccount.getId()));

            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(String.format(
                        "Insufficient balance. Available: %s, Requested: %s",
                        wallet.getBalance(), request.getAmount()));
            }

            // Ensure Asaas rail still configured (no local-only money movement)
            accountAsaasGateway.requireConfiguredSubaccount(managedAccount.getId());

            User actor = userRepository.findById(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));

            wallet.debit(request.getAmount());
            walletRepository.save(wallet);

            Transaction.TransactionBuilder builder = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.PIX)
                    .status(TransactionStatus.PROCESSING)
                    .amount(request.getAmount())
                    .description(request.getDescription())
                    .reference(ProviderCall.referenceOf(request.getDescription()))
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .beneficiary(destination.beneficiary())
                    .createdBy(actor);
            idempotencyService.applyOwner(builder, wallet);
            Transaction transaction = transactionRepository.save(builder.build());

            PixTransaction pixTransaction = PixTransaction.builder()
                    .transaction(transaction)
                    .account(managedAccount)
                    .destinationPixKey(destination.pixKey())
                    .destinationPixKeyType(destination.pixKeyType())
                    .beneficiary(destination.beneficiary())
                    .status(TransactionStatus.PROCESSING)
                    .build();
            pixTransactionRepository.save(pixTransaction);

            ledgerService.postDebit(
                    managedAccount.getId(),
                    request.getAmount(),
                    "ledger:pix:" + idempotencyKey,
                    transaction.getId().toString());

            return transaction;
        });
    }

    private PixTransferResponse resumeTransferProvider(Transaction transaction, UUID accountId) {
        PixTransaction pixTransaction = pixTransactionRepository.findByTransactionId(transaction.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PixTransaction", "transactionId", transaction.getId()));

        if (transaction.getStatus() == TransactionStatus.PENDING_APPROVAL
                || pixTransaction.getStatus() == TransactionStatus.PENDING_APPROVAL) {
            return PixMapper.toTransferResponse(pixTransaction);
        }

        if (transaction.getAsaasPaymentId() != null
                || transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED
                || transaction.getStatus() == TransactionStatus.CANCELLED
                || transaction.getStatus() == TransactionStatus.REVERSED) {
            return PixMapper.toTransferResponse(pixTransaction);
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Transaction attached = tx.execute(status -> attachTransferProvider(transaction.getId(), accountId));
            PixTransaction updated = pixTransactionRepository.findByTransactionId(attached.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "PixTransaction", "transactionId", attached.getId()));
            return PixMapper.toTransferResponse(updated);
        } catch (RuntimeException ex) {
            if (ProviderCall.isTimeout(ex)) {
                log.warn("Provider timeout creating PIX transfer: transactionId={}", transaction.getId());
                throw ex;
            }
            markTransferFailed(transaction.getId());
            throw ex;
        }
    }

    private Transaction attachTransferProvider(UUID transactionId, UUID accountId) {
        Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        if (locked.getAsaasPaymentId() != null) {
            return locked;
        }

        PixTransaction pixTransaction = pixTransactionRepository.findByTransactionIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PixTransaction", "transactionId", transactionId));

        String apiKey = accountAsaasGateway.resolveApiKey(accountId);
        AsaasTransferRequest transferRequest = AsaasTransferRequest.builder()
                .value(locked.getAmount())
                .pixAddressKey(pixTransaction.getDestinationPixKey())
                .pixAddressKeyType(pixTransaction.getDestinationPixKeyType().name())
                .operationType("PIX")
                .description(locked.getDescription() != null ? locked.getDescription() : "PIX transfer")
                .externalReference(locked.getId().toString())
                .build();

        AsaasTransferResponse transferResponse = asaasTransferClient.createTransfer(apiKey, transferRequest);
        locked.setAsaasPaymentId(transferResponse.getId());
        locked.setExternalReference(transferResponse.getId());
        pixTransaction.setProviderReference(transferResponse.getId());
        pixTransactionRepository.save(pixTransaction);
        log.info("PIX transfer submitted: transactionId={}, providerReference={}",
                locked.getId(), transferResponse.getId());
        return transactionRepository.save(locked);
    }

    private void markTransferFailed(UUID transactionId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
            if (locked.getStatus() != TransactionStatus.PROCESSING && locked.getStatus() != TransactionStatus.PENDING) {
                return;
            }
            transactionLifecycleService.transition(locked, TransactionStatus.FAILED);
            pixTransactionRepository.findByTransactionIdForUpdate(transactionId).ifPresent(pt -> {
                pt.setStatus(TransactionStatus.FAILED);
                pixTransactionRepository.save(pt);
            });
            walletService.credit(locked.getWallet().getId(), locked.getAmount());
        });
    }

    private TransferDestination resolveDestination(CreatePixTransferRequest request, Account account) {
        if (request.getBeneficiaryId() != null) {
            Beneficiary beneficiary = beneficiaryRepository.findByIdWithOwner(request.getBeneficiaryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Beneficiary", "id", request.getBeneficiaryId()));
            if (!beneficiary.getOrganization().getId().equals(account.getOrganization().getId())) {
                throw new ForbiddenException("Beneficiary does not belong to the Account organization");
            }
            if (beneficiary.getStatus() != BeneficiaryStatus.ACTIVE) {
                throw new InvalidRequestException("Beneficiary is not ACTIVE");
            }
            if (beneficiary.getPixKey() == null || beneficiary.getPixKeyType() == null) {
                throw new InvalidRequestException("Beneficiary has no PIX destination");
            }
            return new TransferDestination(
                    beneficiary.getPixKey(),
                    beneficiary.getPixKeyType(),
                    beneficiary);
        }
        if (request.getDestinationPixKey() == null || request.getDestinationPixKey().isBlank()
                || request.getDestinationPixKeyType() == null) {
            throw new InvalidRequestException(
                    "Provide beneficiaryId or destinationPixKey + destinationPixKeyType");
        }
        return new TransferDestination(
                request.getDestinationPixKey().trim(),
                request.getDestinationPixKeyType(),
                null);
    }

    private String transferHash(CreatePixTransferRequest request, TransferDestination destination) {
        if (request.getBeneficiaryId() != null) {
            return idempotencyService.hash(
                    TransactionType.PIX.name(),
                    request.getAccountId().toString(),
                    idempotencyService.amountPart(request.getAmount()),
                    request.getBeneficiaryId().toString(),
                    "BRL");
        }
        return idempotencyService.hash(
                TransactionType.PIX.name(),
                request.getAccountId().toString(),
                idempotencyService.amountPart(request.getAmount()),
                destination.pixKey(),
                destination.pixKeyType().name(),
                "BRL");
    }

    private Account requireAccount(Subaccount subaccount, UUID accountId) {
        Account account = subaccount.getAccount();
        if (account != null) {
            return account;
        }
        return loadAccountFromSubaccount(subaccount, accountId);
    }

    private Account loadAccountFromSubaccount(Subaccount subaccount, UUID accountId) {
        // Lazy account may be null if not fetched; re-load via gateway path
        Subaccount reloaded = accountAsaasGateway.requireConfiguredSubaccount(accountId);
        if (reloaded.getAccount() == null) {
            throw new InvalidRequestException("Account is not configured with an Asaas Subaccount");
        }
        return reloaded.getAccount();
    }

    private void assertHierarchicalPixLimits(
            Account account, UUID actorUserId, java.math.BigDecimal amount, UUID excludeTransactionId) {
        transactionLimitService.assertWithinLimits(new LimitContext(
                account.getOrganization().getId(),
                account.getId(),
                actorUserId,
                LimitTransactionType.PIX,
                amount,
                excludeTransactionId));
    }

    private record TransferDestination(String pixKey, PixKeyType pixKeyType, Beneficiary beneficiary) {
    }
}
