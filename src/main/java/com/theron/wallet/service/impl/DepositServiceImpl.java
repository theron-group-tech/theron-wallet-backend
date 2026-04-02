package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasPixQrCodeResponse;
import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.DepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private static final DateTimeFormatter ASAAS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CustomerRepository customerRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;

    @Override
    @Transactional
    public DepositResponse createPixDeposit(DepositRequest request) {
        log.info("Creating PIX deposit: customerId={}, amount={}", request.getCustomerId(), request.getAmount());

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        if (customer.getAsaasCustomerId() == null) {
            throw new ResourceNotFoundException("Customer is not synced with Asaas. Please update customer first.");
        }

        String apiKey = asaasApiKeyResolver.resolveForOutbound(customer.getId());

        Wallet wallet = getOrCreateWallet(customer);

        String idempotencyKey = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            AsaasPaymentRequest paymentRequest = AsaasPaymentRequest.builder()
                    .customer(customer.getAsaasCustomerId())
                    .billingType("PIX")
                    .value(request.getAmount())
                    .dueDate(LocalDate.now().plusDays(1).format(ASAAS_DATE_FORMAT))
                    .description(request.getDescription() != null ? request.getDescription() : "Wallet deposit")
                    .externalReference(transaction.getId().toString())
                    .build();

            AsaasPaymentResponse paymentResponse = asaasPaymentClient.createPayment(apiKey, paymentRequest);

            transaction.setAsaasPaymentId(paymentResponse.getId());
            transaction.setExternalReference(paymentResponse.getId());
            transaction = transactionRepository.save(transaction);

            log.info("PIX deposit created: transactionId={}, asaasPaymentId={}",
                    transaction.getId(), paymentResponse.getId());
        } catch (Exception ex) {
            log.error("Failed to create PIX payment in Asaas: transactionId={}, error={}",
                    transaction.getId(), ex.getMessage());
            throw ex;
        }

        return TransactionMapper.toDepositResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PixQrCodeResponse getPixQrCode(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getAsaasPaymentId() == null) {
            throw new ResourceNotFoundException("PIX QR code not available — payment not created in Asaas");
        }

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ResourceNotFoundException(
                    String.format("PIX QR code not available — transaction status is %s", transaction.getStatus()));
        }

        UUID customerId = transaction.getWallet().getCustomer().getId();
        String apiKey = asaasApiKeyResolver.resolveForOutbound(customerId);

        AsaasPixQrCodeResponse asaasResponse = asaasPaymentClient.getPixQrCode(apiKey, transaction.getAsaasPaymentId());

        return PixQrCodeResponse.builder()
                .encodedImage(asaasResponse.getEncodedImage())
                .payload(asaasResponse.getPayload())
                .expirationDate(asaasResponse.getExpirationDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DepositResponse findById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return TransactionMapper.toDepositResponse(transaction);
    }

    private Wallet getOrCreateWallet(Customer customer) {
        return walletRepository.findByCustomerId(customer.getId())
                .orElseGet(() -> {
                    Wallet wallet = Wallet.builder()
                            .customer(customer)
                            .build();
                    wallet = walletRepository.save(wallet);
                    log.info("Wallet auto-created for customer: customerId={}, walletId={}",
                            customer.getId(), wallet.getId());
                    return wallet;
                });
    }
}
