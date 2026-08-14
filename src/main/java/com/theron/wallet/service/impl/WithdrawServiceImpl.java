package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.LedgerService;
import com.theron.wallet.service.TransactionLifecycleService;
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
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public WithdrawResponse createWithdraw(WithdrawRequest request) {
        log.info("Creating withdrawal: subaccountId={}, amount={}", request.getSubaccountId(), request.getAmount());

        String idempotencyKey = idempotencyService.resolveKey(null, request.getIdempotencyKey());
        String requestHash = withdrawHash(request);

        return idempotencyService.findExisting(idempotencyKey, requestHash)
                .map(existing -> resumeWithdrawProvider(existing, request))
                .orElseGet(() -> createNewWithdraw(request, idempotencyKey, requestHash));
    }

    private WithdrawResponse createNewWithdraw(WithdrawRequest request, String idempotencyKey, String requestHash) {
        Transaction persisted;
        try {
            persisted = persistWithdraw(request, idempotencyKey, requestHash);
        } catch (RuntimeException ex) {
            if (!ProviderCall.isUniqueConstraint(ex)) {
                throw ex;
            }
            persisted = idempotencyService.requireExisting(idempotencyKey, requestHash);
        }
        return resumeWithdrawProvider(persisted, request);
    }

    private Transaction persistWithdraw(WithdrawRequest request, String idempotencyKey, String requestHash) {
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

            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance. Available: %s, Requested: %s",
                                wallet.getBalance(), request.getAmount()));
            }

            wallet.debit(request.getAmount());
            walletRepository.save(wallet);

            Transaction.TransactionBuilder builder = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.WITHDRAWAL)
                    .status(TransactionStatus.PROCESSING)
                    .amount(request.getAmount())
                    .description(request.getDescription())
                    .reference(ProviderCall.referenceOf(request.getDescription()))
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash);
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

        String apiKey = asaasApiKeyResolver.resolveForSubaccount(request.getSubaccountId());
        AsaasTransferRequest transferRequest = AsaasTransferRequest.builder()
                .value(request.getAmount())
                .pixAddressKey(request.getPixAddressKey())
                .pixAddressKeyType(request.getPixAddressKeyType())
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
        return idempotencyService.hash(
                TransactionType.WITHDRAWAL.name(),
                request.getSubaccountId().toString(),
                idempotencyService.amountPart(request.getAmount()),
                request.getPixAddressKey(),
                request.getPixAddressKeyType(),
                "BRL");
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
