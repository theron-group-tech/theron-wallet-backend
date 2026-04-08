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
import com.theron.wallet.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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

    @Override
    @Transactional
    public WithdrawResponse createWithdraw(WithdrawRequest request) {
        log.info("Creating withdrawal: subaccountId={}, amount={}", request.getSubaccountId(), request.getAmount());

        // Idempotency check
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent request detected: idempotencyKey={}, transactionId={}",
                    idempotencyKey, existing.get().getId());
            return TransactionMapper.toWithdrawResponse(existing.get());
        }

        Subaccount subaccount = subaccountRepository.findById(request.getSubaccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", request.getSubaccountId()));

        if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
            throw new InvalidRequestException(
                    "Subaccount is not eligible for withdrawals. Current status: " + subaccount.getStatus());
        }

        String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());

        // Pessimistic lock on wallet to prevent double spending
        Wallet wallet = walletRepository.findBySubaccountIdWithLock(subaccount.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "subaccountId", subaccount.getId()));

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            wallet.getBalance(), request.getAmount()));
        }

        // Debit wallet immediately (rolled back if Asaas call fails)
        wallet.debit(request.getAmount());
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build();
        transaction = transactionRepository.save(transaction);

        try {
            AsaasTransferRequest transferRequest = AsaasTransferRequest.builder()
                    .value(request.getAmount())
                    .pixAddressKey(request.getPixAddressKey())
                    .pixAddressKeyType(request.getPixAddressKeyType())
                    .operationType("PIX")
                    .description(request.getDescription() != null ? request.getDescription() : "Saque da carteira")
                    .externalReference(transaction.getId().toString())
                    .build();

            AsaasTransferResponse transferResponse = asaasTransferClient.createTransfer(apiKey, transferRequest);

            transaction.setAsaasPaymentId(transferResponse.getId());
            transaction.setExternalReference(transferResponse.getId());
            transaction = transactionRepository.save(transaction);

            log.info("Withdrawal created: transactionId={}, asaasTransferId={}",
                    transaction.getId(), transferResponse.getId());
        } catch (Exception ex) {
            log.error("Failed to create transfer in Asaas: transactionId={}, error={}",
                    transaction.getId(), ex.getMessage());
            throw ex;
        }

        return TransactionMapper.toWithdrawResponse(transaction);
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
