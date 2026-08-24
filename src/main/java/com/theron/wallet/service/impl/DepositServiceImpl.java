package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasCreateCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasPixQrCodeResponse;
import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.mapper.TransactionMapper;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.DepositService;
import com.theron.wallet.service.IdempotencyService;
import com.theron.wallet.service.PlatformSplitService;
import com.theron.wallet.service.TransactionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private static final DateTimeFormatter ASAAS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<SubaccountStatus> ALLOWED_STATUSES =
            Set.of(SubaccountStatus.ACTIVE);

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasCustomerClient asaasCustomerClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final IdempotencyService idempotencyService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final PlatformSplitService platformSplitService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public DepositResponse createPixDeposit(DepositRequest request) {
        log.info("Creating PIX deposit: subaccountId={}, amount={}", request.getSubaccountId(), request.getAmount());

        String idempotencyKey = idempotencyService.resolveKey(null, request.getIdempotencyKey());
        String requestHash = depositHash(request);

        return idempotencyService.findExisting(idempotencyKey, requestHash)
                .map(existing -> resumeDepositProvider(existing, request))
                .orElseGet(() -> createNewDeposit(request, idempotencyKey, requestHash));
    }

    private DepositResponse createNewDeposit(DepositRequest request, String idempotencyKey, String requestHash) {
        Transaction persisted;
        try {
            persisted = persistDeposit(request, idempotencyKey, requestHash);
        } catch (RuntimeException ex) {
            if (!ProviderCall.isUniqueConstraint(ex)) {
                throw ex;
            }
            persisted = idempotencyService.requireExisting(idempotencyKey, requestHash);
        }
        return resumeDepositProvider(persisted, request);
    }

    private Transaction persistDeposit(DepositRequest request, String idempotencyKey, String requestHash) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Subaccount subaccount = subaccountRepository.findById(request.getSubaccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", request.getSubaccountId()));

            if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
                throw new InvalidRequestException(
                        "Subaccount is not eligible for deposits. Current status: " + subaccount.getStatus());
            }

            String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
            ensureAsaasCustomer(subaccount, apiKey);
            Wallet wallet = getOrCreateWallet(subaccount);

            Transaction.TransactionBuilder builder = Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.PROCESSING)
                    .amount(request.getAmount())
                    .description(request.getDescription())
                    .reference(ProviderCall.referenceOf(request.getDescription()))
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash);
            idempotencyService.applyOwner(builder, wallet);
            return transactionRepository.save(builder.build());
        });
    }

    private DepositResponse resumeDepositProvider(Transaction transaction, DepositRequest request) {
        if (transaction.getAsaasPaymentId() != null
                || transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED
                || transaction.getStatus() == TransactionStatus.CANCELLED
                || transaction.getStatus() == TransactionStatus.REVERSED) {
            return TransactionMapper.toDepositResponse(transaction);
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Transaction attached = tx.execute(status -> attachDepositProvider(transaction.getId(), request));
            return TransactionMapper.toDepositResponse(attached);
        } catch (RuntimeException ex) {
            if (ProviderCall.isTimeout(ex)) {
                log.warn("Provider timeout creating PIX deposit: transactionId={}", transaction.getId());
                throw ex;
            }
            markDepositFailed(transaction.getId());
            throw ex;
        }
    }

    private Transaction attachDepositProvider(UUID transactionId, DepositRequest request) {
        Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        if (locked.getAsaasPaymentId() != null) {
            return locked;
        }

        Subaccount subaccount = subaccountRepository.findById(request.getSubaccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", request.getSubaccountId()));
        String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
        ensureAsaasCustomer(subaccount, apiKey);

        AsaasPaymentRequest paymentRequest = AsaasPaymentRequest.builder()
                .customer(subaccount.getAsaasCustomerId())
                .billingType("PIX")
                .value(request.getAmount())
                .dueDate(LocalDate.now().plusDays(1).format(ASAAS_DATE_FORMAT))
                .description(request.getDescription() != null ? request.getDescription() : "Depósito na carteira")
                .externalReference(locked.getId().toString())
                .build();
        platformSplitService.applyToPayment(paymentRequest);

        AsaasPaymentResponse paymentResponse = asaasPaymentClient.createPayment(apiKey, paymentRequest);
        locked.setAsaasPaymentId(paymentResponse.getId());
        locked.setExternalReference(paymentResponse.getId());
        log.info("PIX deposit created: transactionId={}, asaasPaymentId={}", locked.getId(), paymentResponse.getId());
        return transactionRepository.save(locked);
    }

    private void markDepositFailed(UUID transactionId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Transaction locked = transactionRepository.findByIdForUpdate(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
            if (locked.getStatus() == TransactionStatus.PROCESSING || locked.getStatus() == TransactionStatus.PENDING) {
                transactionLifecycleService.transition(locked, TransactionStatus.FAILED);
            }
        });
    }

    private String depositHash(DepositRequest request) {
        return idempotencyService.hash(
                TransactionType.DEPOSIT.name(),
                request.getSubaccountId().toString(),
                idempotencyService.amountPart(request.getAmount()),
                "BRL");
    }

    @Override
    @Transactional(readOnly = true)
    public PixQrCodeResponse getPixQrCode(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        if (transaction.getAsaasPaymentId() == null) {
            throw new ResourceNotFoundException("QR Code Pix não disponível — pagamento não criado no Asaas");
        }
        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.PROCESSING) {
            throw new InvalidRequestException(
                    "QR Code Pix não disponível — status da transação: " + transaction.getStatus());
        }

        UUID subaccountId = transaction.getWallet().getSubaccount().getId();
        String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccountId);

        AsaasPixQrCodeResponse asaasResponse =
                asaasPaymentClient.getPixQrCode(apiKey, transaction.getAsaasPaymentId());

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

    @Override
    @Transactional(readOnly = true)
    public Page<DepositResponse> findAll(UUID walletId, UUID subaccountId, Pageable pageable) {
        if (walletId != null) {
            return transactionRepository
                    .findByWalletIdAndType(walletId, TransactionType.DEPOSIT, pageable)
                    .map(TransactionMapper::toDepositResponse);
        }
        if (subaccountId != null) {
            return transactionRepository
                    .findByWallet_Subaccount_IdAndType(subaccountId, TransactionType.DEPOSIT, pageable)
                    .map(TransactionMapper::toDepositResponse);
        }
        throw new InvalidRequestException("Either walletId or subaccountId must be provided");
    }

    private void ensureAsaasCustomer(Subaccount subaccount, String apiKey) {
        if (subaccount.getAsaasCustomerId() != null) {
            return;
        }

        log.info("Creating Asaas customer for subaccount: subaccountId={}", subaccount.getId());

        AsaasCreateCustomerRequest customerRequest = AsaasCreateCustomerRequest.builder()
                .name(subaccount.getName())
                .email(subaccount.getEmail())
                .cpfCnpj(subaccount.getCpfCnpj())
                .mobilePhone(subaccount.getMobilePhone())
                .build();

        AsaasCreateCustomerResponse customerResponse =
                asaasCustomerClient.createCustomer(apiKey, customerRequest);

        subaccount.setAsaasCustomerId(customerResponse.getId());
        subaccountRepository.save(subaccount);

        log.info("Asaas customer created: subaccountId={}, asaasCustomerId={}",
                subaccount.getId(), customerResponse.getId());
    }

    private Wallet getOrCreateWallet(Subaccount subaccount) {
        return walletRepository.findBySubaccountId(subaccount.getId())
                .orElseGet(() -> {
                    Wallet wallet = Wallet.builder()
                            .subaccount(subaccount)
                            .build();
                    wallet = walletRepository.save(wallet);
                    log.info("Wallet criada para subconta: subaccountId={}, walletId={}",
                            subaccount.getId(), wallet.getId());
                    return wallet;
                });
    }
}
