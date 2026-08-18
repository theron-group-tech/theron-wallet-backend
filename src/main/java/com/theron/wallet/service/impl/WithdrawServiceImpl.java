package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Beneficiary;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.LimitTransactionType;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.UnauthorizedException;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.BeneficiaryRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.LimitContext;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.TransactionLimitService;
import com.theron.wallet.service.WalletService;
import com.theron.wallet.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

    private static final Set<SubaccountStatus> ALLOWED_STATUSES =
            Set.of(SubaccountStatus.PENDING_EVALUATION, SubaccountStatus.ACTIVE);

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final AuthorizationService authorizationService;
    private final TransactionLimitService transactionLimitService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public WithdrawResponse createWithdraw(UUID actorUserId, WithdrawRequest request) {
        if (actorUserId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        log.info("Creating withdrawal: subaccountId={}, amount={}", request.getSubaccountId(), request.getAmount());

        String idempotencyKey = idempotencyService.resolveKey(null, request.getIdempotencyKey());
        String requestHash = withdrawHash(request);

        return idempotencyService.findExisting(idempotencyKey, requestHash)
                .map(existing -> resumeWithdrawProvider(existing, request))
                .orElseGet(() -> createNewWithdraw(actorUserId, request, idempotencyKey, requestHash));
    }

    private WithdrawResponse createNewWithdraw(
            UUID actorUserId, WithdrawRequest request, String idempotencyKey, String requestHash) {
        Transaction persisted;
        try {
            persisted = persistWithdraw(actorUserId, request, idempotencyKey, requestHash);
        } catch (RuntimeException ex) {
            if (!ProviderCall.isUniqueConstraint(ex)) {
                throw ex;
            }
            persisted = idempotencyService.requireExisting(idempotencyKey, requestHash);
        }
        return resumeWithdrawProvider(persisted, request);
    }

    private Transaction persistWithdraw(
            UUID actorUserId, WithdrawRequest request, String idempotencyKey, String requestHash) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Subaccount subaccount = subaccountRepository.findById(request.getSubaccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", request.getSubaccountId()));

            if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
                throw new InvalidRequestException(
                        "Subaccount is not eligible for withdrawals. Current status: " + subaccount.getStatus());
            }

            asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());

            Wallet wallet = walletRepository.findBySubaccountIdWithLock(subaccount.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", subaccount.getId()));

            WithdrawDestination destination = resolveDestination(request);
            assertBeneficiaryMatchesWallet(destination.beneficiary(), wallet);

            User actor = userRepository.findById(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
            authorizeAndLimitIfAccountPresent(wallet, actor, request.getAmount());

            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance. Available: %s, Requested: %s",
                                wallet.getBalance(), request.getAmount()));
            }

            wallet.debit(request.getAmount());
            walletRepository.save(wallet);

            Transaction.TransactionBuilder builder = Transaction.builder()
                    .wallet(wallet)
                    .beneficiary(destination.beneficiary())
                    .type(TransactionType.WITHDRAWAL)
                    .status(TransactionStatus.PROCESSING)
                    .amount(request.getAmount())
                    .description(request.getDescription())
                    .reference(ProviderCall.referenceOf(request.getDescription()))
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .createdBy(actor);
            idempotencyService.applyOwner(builder, wallet);
            Transaction transaction = transactionRepository.save(builder.build());

            if (wallet.getAccount() != null) {
                ledgerService.postDebit(
                        wallet.getAccount().getId(),
                        request.getAmount(),
                        "ledger:withdraw:" + idempotencyKey,
                        transaction.getId().toString());
            }
            return transaction;
        });
    }

    private WithdrawResponse resumeWithdrawProvider(Transaction transaction, WithdrawRequest request) {
        if (transaction.getAsaasPaymentId() != null
                || transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED
                || transaction.getStatus() == TransactionStatus.CANCELLED
                || transaction.getStatus() == TransactionStatus.REVERSED) {
            return TransactionMapper.toWithdrawResponse(transaction);
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Transaction attached = tx.execute(status -> attachWithdrawProvider(transaction.getId(), request));
            return TransactionMapper.toWithdrawResponse(attached);
        } catch (RuntimeException ex) {
            if (ProviderCall.isTimeout(ex)) {
                log.warn("Provider timeout creating withdrawal: transactionId={}", transaction.getId());
                throw ex;
            }
            markWithdrawFailed(transaction.getId());
            throw ex;
        }
    }

    private Transaction attachWithdrawProvider(UUID transactionId, WithdrawRequest request) {
        Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        if (locked.getAsaasPaymentId() != null) {
            return locked;
        }

        WithdrawDestination destination = resolveDestination(request);
        String apiKey = asaasApiKeyResolver.resolveForSubaccount(request.getSubaccountId());
        AsaasTransferRequest transferRequest = AsaasTransferRequest.builder()
                .value(request.getAmount())
                .pixAddressKey(destination.pixKey())
                .pixAddressKeyType(destination.pixKeyType())
                .operationType("PIX")
                .description(request.getDescription() != null ? request.getDescription() : "Saque da carteira")
                .externalReference(locked.getId().toString())
                .build();

        AsaasTransferResponse transferResponse = asaasTransferClient.createTransfer(apiKey, transferRequest);
        locked.setAsaasPaymentId(transferResponse.getId());
        locked.setExternalReference(transferResponse.getId());
        log.info("Withdrawal created: transactionId={}, asaasTransferId={}",
                locked.getId(), transferResponse.getId());
        return transactionRepository.save(locked);
    }

    private void markWithdrawFailed(UUID transactionId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
            if (locked.getStatus() != TransactionStatus.PROCESSING && locked.getStatus() != TransactionStatus.PENDING) {
                return;
            }
            transactionLifecycleService.transition(locked, TransactionStatus.FAILED);
            walletService.credit(locked.getWallet().getId(), locked.getAmount());
        });
    }

    private String withdrawHash(WithdrawRequest request) {
        if (request.getBeneficiaryId() != null) {
            return idempotencyService.hash(
                    TransactionType.WITHDRAWAL.name(),
                    request.getSubaccountId().toString(),
                    idempotencyService.amountPart(request.getAmount()),
                    request.getBeneficiaryId().toString(),
                    "BRL");
        }
        return idempotencyService.hash(
                TransactionType.WITHDRAWAL.name(),
                request.getSubaccountId().toString(),
                idempotencyService.amountPart(request.getAmount()),
                request.getPixAddressKey(),
                request.getPixAddressKeyType(),
                "BRL");
    }

    private WithdrawDestination resolveDestination(WithdrawRequest request) {
        if (request.getBeneficiaryId() != null) {
            Beneficiary beneficiary = beneficiaryRepository.findByIdWithOwner(request.getBeneficiaryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", request.getBeneficiaryId()));
            if (beneficiary.getStatus() != BeneficiaryStatus.ACTIVE) {
                throw new InvalidRequestException("Beneficiary is not ACTIVE");
            }
            if (beneficiary.getPixKey() == null || beneficiary.getPixKeyType() == null) {
                throw new InvalidRequestException("Beneficiary has no PIX destination");
            }
            return new WithdrawDestination(
                    beneficiary.getPixKey(),
                    beneficiary.getPixKeyType().name(),
                    beneficiary);
        }
        if (request.getPixAddressKey() == null || request.getPixAddressKey().isBlank()
                || request.getPixAddressKeyType() == null || request.getPixAddressKeyType().isBlank()) {
            throw new InvalidRequestException("Provide beneficiaryId or a PIX destination (pixAddressKey + pixAddressKeyType)");
        }
        return new WithdrawDestination(
                request.getPixAddressKey().trim(),
                request.getPixAddressKeyType().trim(),
                null);
    }

    private static void assertBeneficiaryMatchesWallet(Beneficiary beneficiary, Wallet wallet) {
        if (beneficiary == null) {
            return;
        }
        Account resolved = wallet.getAccount();
        if (resolved == null && wallet.getSubaccount() != null && wallet.getSubaccount().getAccount() != null) {
            resolved = wallet.getSubaccount().getAccount();
        }
        if (resolved == null) {
            throw new ForbiddenException("Wallet is not linked to an organization");
        }
        UUID walletOrgId = resolved.getOrganization().getId();
        UUID beneficiaryOrgId = beneficiary.getOrganization().getId();
        if (!walletOrgId.equals(beneficiaryOrgId)) {
            throw new ForbiddenException("Beneficiary does not belong to the wallet organization");
        }
    }

    private void authorizeAndLimitIfAccountPresent(Wallet wallet, User actor, java.math.BigDecimal amount) {
        Account resolved = wallet.getAccount();
        if (resolved == null && wallet.getSubaccount() != null && wallet.getSubaccount().getAccount() != null) {
            resolved = wallet.getSubaccount().getAccount();
        }
        if (resolved == null) {
            throw new ForbiddenException("Wallet is not linked to an organization");
        }
        final UUID accountId = resolved.getId();
        Account managed = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        UUID orgId = managed.getOrganization().getId();
        authorizationService.requirePermission(orgId, actor.getId(), PermissionCodes.WALLET_TRANSFER);
        transactionLimitService.assertWithinLimits(new LimitContext(
                orgId,
                managed.getId(),
                actor.getId(),
                LimitTransactionType.WITHDRAWAL,
                amount,
                null));
    }

    private record WithdrawDestination(String pixKey, String pixKeyType, Beneficiary beneficiary) {
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawResponse findById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return TransactionMapper.toWithdrawResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WithdrawResponse> findAll(UUID walletId, UUID subaccountId, Pageable pageable) {
        if (walletId != null) {
            return transactionRepository
                    .findByWalletIdAndType(walletId, TransactionType.WITHDRAWAL, pageable)
                    .map(TransactionMapper::toWithdrawResponse);
        }
        if (subaccountId != null) {
            return transactionRepository
                    .findByWallet_Subaccount_IdAndType(subaccountId, TransactionType.WITHDRAWAL, pageable)
                    .map(TransactionMapper::toWithdrawResponse);
        }
        throw new InvalidRequestException("Either walletId or subaccountId must be provided");
    }
}
