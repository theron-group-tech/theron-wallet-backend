package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.asaas.AsaasPixExternalKeyResponse;
import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.request.CreatePlatformPixTransferRequest;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
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
import com.theron.wallet.enums.TransactionType;
import com.theron.wallet.exception.InvalidRequestException;
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
    public void applyWebhookStatus(String asaasTransferId, String event) {
        if (asaasTransferId == null || asaasTransferId.isBlank()) {
            return;
        }
        platformPixTransferRepository.findByAsaasTransferIdForUpdate(asaasTransferId).ifPresent(row -> {
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
