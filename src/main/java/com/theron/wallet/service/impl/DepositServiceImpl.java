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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            Set.of(SubaccountStatus.PENDING_EVALUATION, SubaccountStatus.ACTIVE);

    private final SubaccountRepository subaccountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AsaasPaymentClient asaasPaymentClient;
    private final AsaasCustomerClient asaasCustomerClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;

    @Override
    @Transactional
    public DepositResponse createPixDeposit(DepositRequest request) {
        log.info("Creating PIX deposit: subaccountId={}, amount={}", request.getSubaccountId(), request.getAmount());

        // 1. Load and validate subaccount
        Subaccount subaccount = subaccountRepository.findById(request.getSubaccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", request.getSubaccountId()));

        if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
            throw new InvalidRequestException(
                    "Subaccount is not eligible for deposits. Current status: " + subaccount.getStatus());
        }

        // 2. Decrypt subaccount's Asaas API key (tenant isolation)
        String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());

        // 3. Ensure Asaas customer exists for this subaccount (lazy creation)
        ensureAsaasCustomer(subaccount, apiKey);

        // 4. Get or create wallet
        Wallet wallet = getOrCreateWallet(subaccount);

        // 5. Create PENDING transaction
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .description(request.getDescription())
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        transaction = transactionRepository.save(transaction);

        try {
            // 6. Create Pix charge in Asaas using the subaccount's key
            AsaasPaymentRequest paymentRequest = AsaasPaymentRequest.builder()
                    .customer(subaccount.getAsaasCustomerId())
                    .billingType("PIX")
                    .value(request.getAmount())
                    .dueDate(LocalDate.now().plusDays(1).format(ASAAS_DATE_FORMAT))
                    .description(request.getDescription() != null ? request.getDescription() : "Depósito na carteira")
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
            throw new ResourceNotFoundException("QR Code Pix não disponível — pagamento não criado no Asaas");
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
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

    /**
     * Lazily creates an Asaas customer for the subaccount if one doesn't exist yet.
     * The customer represents the subaccount holder as the payer in Asaas charges.
     * Saves {@code asaasCustomerId} back to the subaccount entity.
     */
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
