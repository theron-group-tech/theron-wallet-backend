package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InsufficientBalanceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

    private final CustomerRepository customerRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;

    @Override
    @Transactional
    public WithdrawResponse createWithdraw(WithdrawRequest request) {
        log.info("Creating withdrawal: customerId={}, amount={}", request.getCustomerId(), request.getAmount());

        // Idempotency check — return existing transaction if key already used
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent request detected: idempotencyKey={}, transactionId={}",
                    idempotencyKey, existing.get().getId());
            return TransactionMapper.toWithdrawResponse(existing.get());
        }

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        if (customer.getAsaasCustomerId() == null) {
            throw new ResourceNotFoundException("Customer is not synced with Asaas. Please update customer first.");
        }

        // Resolve tenant API key — blocks if subaccount is EVALUATION_BLOCKED
        String apiKey = asaasApiKeyResolver.resolveForOutbound(customer.getId());

        // Pessimistic lock on wallet to prevent double spending
        Wallet wallet = walletRepository.findByCustomerId(customer.getId())
                .map(w -> walletRepository.findByIdForUpdate(w.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Wallet", "customerId", customer.getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "customerId", customer.getId()));

        // Validate balance — only confirmed balance can be withdrawn
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: %s, Requested: %s",
                            wallet.getBalance(), request.getAmount()));
        }

        // Debit wallet immediately (will be rolled back if Asaas call fails)
        wallet.debit(request.getAmount());
        walletRepository.save(wallet);

        // Create PENDING withdrawal transaction
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
                    .description(request.getDescription() != null ? request.getDescription() : "Wallet withdrawal")
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
}
