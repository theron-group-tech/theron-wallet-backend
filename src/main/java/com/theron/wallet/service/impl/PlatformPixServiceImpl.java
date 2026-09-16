package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasPixExternalKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasPixTransactionResponse;
import com.theron.wallet.dto.request.CreatePlatformPixPayQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixTransferRequest;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
import com.theron.wallet.dto.response.PlatformPixPayQrCodeResponse;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.dto.response.PlatformPixTransferResponse;
import com.theron.wallet.entity.PixKey;
import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.Wallet;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.enums.PixKeyStatus;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.util.PixEmvPayloadUtils;
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.mapper.PixKeyLookupMapper;
import com.theron.wallet.repository.PixKeyRepository;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.repository.WalletRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.NotificationService;
import com.theron.wallet.service.PlatformPixService;
import com.theron.wallet.service.TransactionLifecycleService;
import com.theron.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformPixServiceImpl implements PlatformPixService {

    static final String QR_PAY_DESTINATION_FALLBACK = "QR_CODE_PAY";
    private static final int QR_PAY_BIND_WINDOW_MINUTES = 10;
    private static final Set<String> COMPLETED_REMOTE = Set.of("DONE");
    private static final Set<String> FAILED_REMOTE = Set.of("FAILED", "CANCELLED", "BLOCKED");
    private static final Set<String> PROCESSING_REMOTE = Set.of(
            "PENDING", "BANK_PROCESSING", "AWAITING_RISK_ANALYSIS");

    private final AsaasPixClient asaasPixClient;
    private final AsaasTransferClient asaasTransferClient;
    private final AsaasProperties asaasProperties;
    private final PlatformPixTransferRepository platformPixTransferRepository;
    private final PixKeyRepository pixKeyRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public List<PlatformPixKeyResponse> listKeys() {
        String masterKey = requireMasterApiKey();
        AsaasListResponse<AsaasPixKeyResponse> response = asaasPixClient.listPixKeys(masterKey);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream().map(this::toKeyResponse).toList();
    }

    @Override
    public PlatformPixKeyResponse createKey(CreatePlatformPixKeyRequest request) {
        request.getType().requireCreatableViaProvider();
        String masterKey = requireMasterApiKey();
        AsaasPixKeyResponse created = asaasPixClient.createPixKey(
                masterKey,
                AsaasPixKeyRequest.builder().type(PixKeyType.EVP.name()).build());
        log.info("Created Platform Account PIX key in Asaas: id={}", created.getId());
        return toKeyResponse(created);
    }

    @Override
    public void deleteKey(String asaasPixKeyId) {
        if (asaasPixKeyId == null || asaasPixKeyId.isBlank()) {
            throw new InvalidRequestException("PIX key id is required");
        }
        String masterKey = requireMasterApiKey();
        asaasPixClient.deletePixKey(masterKey, asaasPixKeyId.trim());
        log.info("Deleted Platform Account PIX key in Asaas: id={}", asaasPixKeyId);
    }

    @Override
    public PixKeyLookupResponse checkKey(PixKeyType type, String key) {
        if (type == null) {
            throw new InvalidRequestException("PIX key type is required");
        }
        if (key == null || key.isBlank()) {
            throw new InvalidRequestException("PIX key is required");
        }
        String masterKey = requireMasterApiKey();
        AsaasPixExternalKeyResponse asaas = asaasPixClient.lookupExternalKey(
                masterKey, type.name(), key.trim());
        return PixKeyLookupMapper.toResponse(asaas);
    }

    @Override
    public AccountPixQrCodeResponse createQrCode(CreatePlatformPixQrCodeRequest request) {
        if (request.getPixKeyId() == null || request.getPixKeyId().isBlank()) {
            throw new InvalidRequestException("pixKeyId is required");
        }
        String masterKey = requireMasterApiKey();
        String addressKey = resolveAddressKey(masterKey, request.getPixKeyId().trim());
        AsaasPixStaticQrCodeResponse asaasResponse = asaasPixClient.createStaticQrCode(
                masterKey,
                addressKey,
                PixEmvPayloadUtils.buildStaticQrCodeRequest(
                        request.getValue(), request.getDescription()));
        log.info("Created Platform Account static PIX QR code: pixKeyId={}", request.getPixKeyId());
        return AccountPixQrCodeResponse.builder()
                .payload(asaasResponse.getPayload())
                .encodedImage(asaasResponse.getEncodedImage())
                .expirationDate(asaasResponse.getExpirationDate())
                .value(PixEmvPayloadUtils.resolveQrCodeValue(
                        asaasResponse.getValue(), request.getValue(), asaasResponse.getPayload()))
                .description(asaasResponse.getDescription())
                .build();
    }

    @Override
    public PlatformPixPayQrCodeResponse payQrCode(
            CreatePlatformPixPayQrCodeRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for Platform PIX QR payments");
        }
        String normalizedKey = idempotencyKey.trim();
        Optional<PlatformPixTransfer> existing =
                platformPixTransferRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            return toPayQrCodeResponseFromTransfer(existing.get());
        }

        String payload = request.getPayload() != null ? request.getPayload().trim() : "";
        if (payload.isEmpty()) {
            throw new InvalidRequestException("payload is required");
        }
        if (!payload.startsWith("000201")) {
            throw new InvalidRequestException("payload must be a valid PIX copia e cola (EMV) string");
        }

        BigDecimal payloadAmount = PixEmvPayloadUtils.parseTransactionAmount(payload);
        BigDecimal payAmount = request.getAmount();
        if (payloadAmount != null) {
            if (payAmount != null && payAmount.compareTo(payloadAmount) != 0) {
                throw new InvalidRequestException(
                        "Amount must match QR code value (R$ " + payloadAmount.toPlainString() + ")");
            }
            payAmount = payloadAmount;
        } else if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("amount is required for open QR codes");
        }

        String masterKey = requireMasterApiKey();
        String description = request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription().trim()
                : "Platform PIX QR payment";

        AsaasPixPayQrCodeRequest asaasRequest = AsaasPixPayQrCodeRequest.builder()
                .qrCode(AsaasPixPayQrCodeRequest.QrCodePayload.builder()
                        .payload(payload)
                        .build())
                .value(payAmount)
                .description(description)
                .externalReference(normalizedKey)
                .build();

        // Commit before Asaas so transfer-validation can APPROVE during the pay HTTP call.
        reserveQrPayAuthorization(normalizedKey, payAmount, description);

        AsaasPixPayQrCodeResponse asaasResponse = asaasPixClient.payQrCode(
                masterKey, asaasRequest, normalizedKey);
        log.info("Paid Platform Account PIX QR code in Asaas: id={}, status={}, transferId={}",
                asaasResponse.getId(), asaasResponse.getStatus(), asaasResponse.getTransferId());

        PlatformPixTransfer saved = completeQrPayAuthorization(
                normalizedKey, asaasResponse, payAmount, description);
        if (saved.getStatus() == TransactionStatus.COMPLETED) {
            creditInNewTransaction(saved.getId());
        }
        return toPayQrCodeResponse(asaasResponse, description);
    }

    private PlatformPixTransfer reserveQrPayAuthorization(
            String idempotencyKey, BigDecimal payAmount, String description) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return requiresNew.execute(statusTx -> {
            Optional<PlatformPixTransfer> raced =
                    platformPixTransferRepository.findByIdempotencyKey(idempotencyKey);
            if (raced.isPresent()) {
                return raced.get();
            }
            return platformPixTransferRepository.save(PlatformPixTransfer.builder()
                    .amount(payAmount)
                    .status(TransactionStatus.PROCESSING)
                    .destinationPixKey(QR_PAY_DESTINATION_FALLBACK)
                    .destinationPixKeyType(PixKeyType.EVP)
                    .description(description)
                    .idempotencyKey(idempotencyKey)
                    .build());
        });
    }

    private PlatformPixTransfer completeQrPayAuthorization(
            String idempotencyKey,
            AsaasPixPayQrCodeResponse asaasResponse,
            BigDecimal payAmount,
            String description) {
        TransactionStatus status = mapPixPayStatus(asaasResponse.getStatus());
        String destinationKey = resolveQrPayDestinationKey(asaasResponse);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return requiresNew.execute(statusTx -> {
            PlatformPixTransfer row = platformPixTransferRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing reserved QR pay authorization: " + idempotencyKey));
            if (asaasResponse.getId() != null && !asaasResponse.getId().isBlank()) {
                row.setAsaasPixTransactionId(asaasResponse.getId());
            }
            if (asaasResponse.getTransferId() != null && !asaasResponse.getTransferId().isBlank()) {
                row.setAsaasTransferId(asaasResponse.getTransferId());
            }
            row.setAmount(asaasResponse.getValue() != null ? asaasResponse.getValue() : payAmount);
            row.setStatus(status);
            row.setDestinationPixKey(destinationKey);
            if (description != null && !description.isBlank()) {
                row.setDescription(description);
            }
            return platformPixTransferRepository.save(row);
        });
    }

    @Override
    public Optional<PlatformPixTransfer> bindAndFindPlatformTransferForValidation(
            String transferId, String externalReference, BigDecimal amount) {
        if (transferId == null || transferId.isBlank()) {
            return Optional.empty();
        }
        Optional<PlatformPixTransfer> byTransferId =
                platformPixTransferRepository.findByAsaasTransferId(transferId);
        if (byTransferId.isPresent()) {
            return byTransferId;
        }
        if (externalReference != null && !externalReference.isBlank()) {
            Optional<PlatformPixTransfer> byIdempotency =
                    platformPixTransferRepository.findByIdempotencyKey(externalReference.trim());
            if (byIdempotency.isPresent()) {
                return Optional.of(bindTransferId(byIdempotency.get(), transferId));
            }
        }
        if (amount != null) {
            LocalDateTime since = LocalDateTime.now().minusMinutes(QR_PAY_BIND_WINDOW_MINUTES);
            List<PlatformPixTransfer> pending = platformPixTransferRepository.findPendingQrPayForBind(
                    QR_PAY_DESTINATION_FALLBACK,
                    List.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING),
                    amount,
                    since);
            if (!pending.isEmpty()) {
                return Optional.of(bindTransferId(pending.getFirst(), transferId));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<PlatformPixTransfer> findPlatformPixQrPayForValidation(
            String pixTransactionId, BigDecimal amount) {
        if (pixTransactionId != null && !pixTransactionId.isBlank()) {
            Optional<PlatformPixTransfer> byPixTx =
                    platformPixTransferRepository.findByAsaasPixTransactionId(pixTransactionId.trim());
            if (byPixTx.isPresent()) {
                return byPixTx;
            }
        }
        if (amount != null) {
            LocalDateTime since = LocalDateTime.now().minusMinutes(QR_PAY_BIND_WINDOW_MINUTES);
            List<PlatformPixTransfer> pending = platformPixTransferRepository.findPendingQrPayForBind(
                    QR_PAY_DESTINATION_FALLBACK,
                    List.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING),
                    amount,
                    since);
            if (!pending.isEmpty()) {
                return Optional.of(pending.getFirst());
            }
        }
        return Optional.empty();
    }

    private PlatformPixTransfer bindTransferId(PlatformPixTransfer row, String transferId) {
        if (row.getAsaasTransferId() == null || row.getAsaasTransferId().isBlank()) {
            row.setAsaasTransferId(transferId);
            log.info("Bound asaas_transfer_id to platform_pix_transfer: id={}, transferId={}",
                    row.getId(), transferId);
            return platformPixTransferRepository.save(row);
        }
        return row;
    }

    private static String resolveQrPayDestinationKey(AsaasPixPayQrCodeResponse asaasResponse) {
        if (asaasResponse.getExternalAccount() != null
                && asaasResponse.getExternalAccount().getAddressKey() != null
                && !asaasResponse.getExternalAccount().getAddressKey().isBlank()) {
            return asaasResponse.getExternalAccount().getAddressKey().trim();
        }
        return QR_PAY_DESTINATION_FALLBACK;
    }

    private PlatformPixPayQrCodeResponse toPayQrCodeResponseFromTransfer(PlatformPixTransfer row) {
        return PlatformPixPayQrCodeResponse.builder()
                .id(row.getAsaasPixTransactionId() != null
                        ? row.getAsaasPixTransactionId()
                        : row.getAsaasTransferId())
                .amount(row.getAmount())
                .status(row.getStatus())
                .providerStatus(row.getStatus() != null ? row.getStatus().name() : null)
                .transferId(row.getAsaasTransferId())
                .description(row.getDescription())
                .build();
    }

    @Override
    public PlatformPixPayQrCodeResponse getPixTransaction(String asaasPixTransactionId) {
        if (asaasPixTransactionId == null || asaasPixTransactionId.isBlank()) {
            throw new InvalidRequestException("transaction id is required");
        }
        String masterKey = requireMasterApiKey();
        AsaasPixTransactionResponse asaas = asaasPixClient.retrievePixTransaction(
                masterKey, asaasPixTransactionId.trim());
        return toPixTransactionResponse(asaas);
    }

    @Override
    public PlatformPixTransferResponse createTransfer(
            CreatePlatformPixTransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for Platform PIX transfers");
        }
        String normalizedKey = idempotencyKey.trim();
        Optional<PlatformPixTransfer> existing =
                platformPixTransferRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == TransactionStatus.COMPLETED
                    && existing.get().getCreditTransactionId() == null) {
                creditInNewTransaction(existing.get().getId());
            }
            return toTransferResponse(existing.get());
        }

        String destinationKey = request.getDestinationPixKey() != null
                ? request.getDestinationPixKey().trim()
                : null;
        if (destinationKey == null || destinationKey.isEmpty()) {
            throw new InvalidRequestException("destinationPixKey is required");
        }
        if (request.getDestinationPixKeyType() == null) {
            throw new InvalidRequestException("destinationPixKeyType is required");
        }

        String masterKey = requireMasterApiKey();
        String description = request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription().trim()
                : "Platform PIX transfer";

        AsaasTransferRequest transferRequest = AsaasTransferRequest.builder()
                .value(request.getAmount())
                .pixAddressKey(destinationKey)
                .pixAddressKeyType(request.getDestinationPixKeyType().name())
                .operationType("PIX")
                .description(description)
                .externalReference(normalizedKey)
                .build();

        AsaasTransferResponse created = asaasTransferClient.createTransfer(
                masterKey, transferRequest, normalizedKey);
        log.info("Created Platform Account PIX transfer in Asaas: id={}, status={}",
                created.getId(), created.getStatus());

        // Commit immediately so Asaas transfer-validation can APPROVE before this HTTP returns.
        TransactionStatus status = mapTransferStatus(created.getStatus());
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        PlatformPixTransfer saved = requiresNew.execute(statusTx -> {
            Optional<PlatformPixTransfer> raced =
                    platformPixTransferRepository.findByIdempotencyKey(normalizedKey);
            if (raced.isPresent()) {
                return raced.get();
            }
            return platformPixTransferRepository.save(PlatformPixTransfer.builder()
                    .asaasTransferId(created.getId())
                    .amount(created.getValue() != null ? created.getValue() : request.getAmount())
                    .status(status)
                    .destinationPixKey(destinationKey)
                    .destinationPixKeyType(request.getDestinationPixKeyType())
                    .description(description)
                    .idempotencyKey(normalizedKey)
                    .build());
        });
        if (saved == null) {
            throw new InvalidRequestException("Failed to persist Platform PIX transfer");
        }
        if (saved.getStatus() == TransactionStatus.COMPLETED) {
            creditInNewTransaction(saved.getId());
        }
        return toTransferResponse(saved);
    }

    @Override
    public Page<PlatformPixTransferResponse> listTransfers(Pageable pageable) {
        String masterKey = requireMasterApiKey();
        int offset = (int) pageable.getOffset();
        int limit = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        AsaasListResponse<AsaasTransferResponse> response =
                asaasTransferClient.listTransfers(masterKey, offset, limit);
        if (response == null || response.getData() == null) {
            return Page.empty(pageable);
        }
        List<PlatformPixTransferResponse> content = response.getData().stream()
                .map(item -> toTransferResponse(item, null, null, item.getDescription()))
                .toList();
        long total = response.getTotalCount() != null
                ? response.getTotalCount().longValue()
                : content.size();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional
    public void applyWebhookStatus(
            String asaasTransferId, String event, String externalReference, BigDecimal value) {
        if (asaasTransferId == null || asaasTransferId.isBlank()) {
            return;
        }
        Optional<PlatformPixTransfer> rowOpt =
                platformPixTransferRepository.findByAsaasTransferIdForUpdate(asaasTransferId);
        if (rowOpt.isEmpty()) {
            rowOpt = bindAndFindPlatformTransferForValidation(asaasTransferId, externalReference, value)
                    .flatMap(row -> platformPixTransferRepository.findByAsaasTransferIdForUpdate(asaasTransferId));
        }
        rowOpt.ifPresent(row -> {
            TransactionStatus next = mapWebhookEventToStatus(event);
            if (next != null && row.getStatus() != next) {
                row.setStatus(next);
                platformPixTransferRepository.save(row);
                log.info("Updated platform_pix_transfer status: asaasTransferId={}, status={}",
                        asaasTransferId, next);
            }
            if (row.getStatus() == TransactionStatus.COMPLETED) {
                maybeCreditInternalDestination(row);
            }
        });
    }

    @Override
    @Transactional
    public int reconcileCredits() {
        int credited = 0;
        List<PlatformPixTransfer> rows =
                platformPixTransferRepository.findByStatusAndCreditTransactionIdIsNull(
                        TransactionStatus.COMPLETED);
        for (PlatformPixTransfer row : rows) {
            if (maybeCreditInternalDestination(row)) {
                credited++;
            }
        }
        return credited;
    }

    private boolean creditInNewTransaction(UUID platformTransferId) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean credited = requiresNew.execute(status -> platformPixTransferRepository
                .findByIdForUpdate(platformTransferId)
                .map(this::maybeCreditInternalDestination)
                .orElse(false));
        return Boolean.TRUE.equals(credited);
    }

    private boolean maybeCreditInternalDestination(PlatformPixTransfer row) {
        if (row.getStatus() != TransactionStatus.COMPLETED || row.getCreditTransactionId() != null) {
            return false;
        }

        PixKey destination = pixKeyRepository
                .findByKeyAndStatus(row.getDestinationPixKey(), PixKeyStatus.ACTIVE)
                .orElse(null);
        if (destination == null) {
            return false;
        }

        String creditIdempotencyKey = "asaas:platform-pix:in:" + row.getAsaasTransferId();
        Optional<Transaction> existing =
                transactionRepository.findByAsaasPaymentId(row.getAsaasTransferId());
        if (existing.isEmpty()) {
            existing = transactionRepository.findByIdempotencyKey(creditIdempotencyKey);
        }

        if (existing.isPresent()) {
            Transaction transaction = existing.get();
            boolean credited = completeExistingCredit(transaction);
            if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                row.setCreditTransactionId(transaction.getId());
                platformPixTransferRepository.save(row);
            }
            return credited;
        }

        Wallet wallet = walletRepository.findByAccountIdWithLock(destination.getAccount().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet not found for internal PIX destination account"));
        Transaction transaction = transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .account(destination.getAccount())
                .organization(destination.getOrganization())
                .type(TransactionType.TRANSFER_IN)
                .status(TransactionStatus.PENDING)
                .amount(row.getAmount())
                .currency("BRL")
                .description(row.getDescription() != null
                        ? row.getDescription()
                        : "Transferência PIX recebida da conta Master")
                .asaasPaymentId(row.getAsaasTransferId())
                .externalReference(row.getIdempotencyKey())
                .idempotencyKey(creditIdempotencyKey)
                .build());
        transaction = transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        walletService.credit(wallet.getId(), row.getAmount());
        row.setCreditTransactionId(transaction.getId());
        platformPixTransferRepository.save(row);
        notifyReceived(transaction);
        log.info("Credited internal Platform PIX destination: asaasTransferId={}, transactionId={}, accountId={}",
                row.getAsaasTransferId(), transaction.getId(), destination.getAccount().getId());
        return true;
    }

    private boolean completeExistingCredit(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            return false;
        }
        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Cannot reconcile Platform PIX credit from terminal transaction: transactionId={}, status={}",
                    transaction.getId(), transaction.getStatus());
            return false;
        }
        transactionLifecycleService.transition(transaction, TransactionStatus.COMPLETED);
        walletService.credit(transaction.getWallet().getId(), transaction.getAmount());
        notifyReceived(transaction);
        return true;
    }

    private void notifyReceived(Transaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", transaction.getAmount().toPlainString());
        data.put("transactionId", transaction.getId().toString());
        data.put("type", transaction.getType().name());
        notificationService.notifyUsersWithPermission(
                transaction.getOrganization().getId(),
                PermissionCodes.WALLET_READ,
                null,
                NotificationType.TRANSFER_RECEIVED,
                transaction.getId(),
                data);
    }

    private String resolveAddressKey(String apiKey, String asaasPixKeyId) {
        AsaasListResponse<AsaasPixKeyResponse> response = asaasPixClient.listPixKeys(apiKey);
        if (response == null || response.getData() == null) {
            throw new ResourceNotFoundException("PixKey", "id", asaasPixKeyId);
        }
        return response.getData().stream()
                .filter(key -> asaasPixKeyId.equals(key.getId()))
                .map(AsaasPixKeyResponse::getKey)
                .filter(key -> key != null && !key.isBlank())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PixKey", "id", asaasPixKeyId));
    }

    private String requireMasterApiKey() {
        String key = asaasProperties.getKey();
        if (key == null || key.isBlank()) {
            throw new InvalidRequestException("ASAAS_API_KEY is not configured");
        }
        return key;
    }

    private PlatformPixKeyResponse toKeyResponse(AsaasPixKeyResponse asaas) {
        PixKeyType type = parseType(asaas.getType() != null ? asaas.getType() : asaas.getPixKeyType());
        return PlatformPixKeyResponse.builder()
                .id(asaas.getId())
                .type(type)
                .key(asaas.getKey())
                .status(asaas.getStatus())
                .build();
    }

    private PlatformPixPayQrCodeResponse toPayQrCodeResponse(
            AsaasPixPayQrCodeResponse asaas, String fallbackDescription) {
        AsaasPixPayQrCodeResponse.ExternalAccount recipient = asaas.getExternalAccount();
        return PlatformPixPayQrCodeResponse.builder()
                .id(asaas.getId())
                .amount(asaas.getValue())
                .status(mapPixPayStatus(asaas.getStatus()))
                .providerStatus(asaas.getStatus())
                .transferId(asaas.getTransferId())
                .refusalReason(asaas.getRefusalReason())
                .recipientName(recipient != null ? recipient.getName() : null)
                .recipientDocument(recipient != null ? recipient.getCpfCnpj() : null)
                .institutionName(recipient != null ? recipient.getIspbName() : null)
                .description(asaas.getDescription() != null ? asaas.getDescription() : fallbackDescription)
                .endToEndIdentifier(asaas.getEndToEndIdentifier())
                .build();
    }

    private PlatformPixPayQrCodeResponse toPixTransactionResponse(AsaasPixTransactionResponse asaas) {
        AsaasPixTransactionResponse.ExternalAccount recipient = asaas.getExternalAccount();
        return PlatformPixPayQrCodeResponse.builder()
                .id(asaas.getId())
                .amount(asaas.getValue())
                .status(mapPixPayStatus(asaas.getStatus()))
                .providerStatus(asaas.getStatus())
                .transferId(asaas.getTransferId())
                .refusalReason(asaas.getRefusalReason())
                .recipientName(recipient != null ? recipient.getName() : null)
                .recipientDocument(recipient != null ? recipient.getCpfCnpj() : null)
                .institutionName(recipient != null ? recipient.getIspbName() : null)
                .description(asaas.getDescription())
                .endToEndIdentifier(asaas.getEndToEndIdentifier())
                .build();
    }

    private PlatformPixTransferResponse toTransferResponse(PlatformPixTransfer row) {
        return PlatformPixTransferResponse.builder()
                .id(row.getAsaasTransferId())
                .amount(row.getAmount())
                .status(row.getStatus())
                .destinationPixKey(row.getDestinationPixKey())
                .destinationPixKeyType(row.getDestinationPixKeyType())
                .providerReference(row.getAsaasTransferId())
                .description(row.getDescription())
                .createdAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null)
                .build();
    }

    private PlatformPixTransferResponse toTransferResponse(
            AsaasTransferResponse asaas,
            String fallbackDestinationKey,
            PixKeyType fallbackDestinationType,
            String fallbackDescription) {
        String destinationKey = asaas.getPixAddressKey() != null
                ? asaas.getPixAddressKey()
                : fallbackDestinationKey;
        PixKeyType destinationType = parseTypeOrNull(
                asaas.getPixAddressKeyType() != null
                        ? asaas.getPixAddressKeyType()
                        : (fallbackDestinationType != null ? fallbackDestinationType.name() : null));
        String description = asaas.getDescription() != null ? asaas.getDescription() : fallbackDescription;
        return PlatformPixTransferResponse.builder()
                .id(asaas.getId())
                .amount(asaas.getValue())
                .status(mapTransferStatus(asaas.getStatus()))
                .destinationPixKey(destinationKey)
                .destinationPixKeyType(destinationType)
                .providerReference(asaas.getId())
                .description(description)
                .createdAt(asaas.getDateCreated())
                .build();
    }

    static TransactionStatus mapTransferStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransactionStatus.PROCESSING;
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (COMPLETED_REMOTE.contains(status)) {
            return TransactionStatus.COMPLETED;
        }
        if (FAILED_REMOTE.contains(status)) {
            if ("CANCELLED".equals(status)) {
                return TransactionStatus.CANCELLED;
            }
            return TransactionStatus.FAILED;
        }
        if (PROCESSING_REMOTE.contains(status)) {
            return TransactionStatus.PROCESSING;
        }
        return TransactionStatus.PROCESSING;
    }

    static TransactionStatus mapPixPayStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransactionStatus.PROCESSING;
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if ("DONE".equals(status)) {
            return TransactionStatus.COMPLETED;
        }
        if ("REFUSED".equals(status) || "CANCELLED".equals(status)) {
            return "CANCELLED".equals(status) ? TransactionStatus.CANCELLED : TransactionStatus.FAILED;
        }
        return TransactionStatus.PROCESSING;
    }

    static TransactionStatus mapWebhookEventToStatus(String event) {
        if (event == null || event.isBlank()) {
            return null;
        }
        return switch (event.trim().toUpperCase(Locale.ROOT)) {
            case "TRANSFER_DONE" -> TransactionStatus.COMPLETED;
            case "TRANSFER_FAILED", "TRANSFER_BLOCKED" -> TransactionStatus.FAILED;
            case "TRANSFER_CANCELLED" -> TransactionStatus.CANCELLED;
            case "TRANSFER_CREATED", "TRANSFER_PENDING", "TRANSFER_IN_BANK_PROCESSING" ->
                    TransactionStatus.PROCESSING;
            default -> null;
        };
    }

    private static PixKeyType parseType(String raw) {
        PixKeyType parsed = parseTypeOrNull(raw);
        return parsed != null ? parsed : PixKeyType.EVP;
    }

    private static PixKeyType parseTypeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PixKeyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
