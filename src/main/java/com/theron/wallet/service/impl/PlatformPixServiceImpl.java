package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.request.CreatePlatformPixTransferRequest;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.dto.response.PlatformPixTransferResponse;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.integration.AsaasTransferClient;
import com.theron.wallet.service.PlatformPixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    public PlatformPixTransferResponse createTransfer(
            CreatePlatformPixTransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for Platform PIX transfers");
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
                .externalReference(idempotencyKey.trim())
                .build();

        AsaasTransferResponse created = asaasTransferClient.createTransfer(
                masterKey, transferRequest, idempotencyKey.trim());
        log.info("Created Platform Account PIX transfer in Asaas: id={}, status={}",
                created.getId(), created.getStatus());
        return toTransferResponse(
                created, destinationKey, request.getDestinationPixKeyType(), description);
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
