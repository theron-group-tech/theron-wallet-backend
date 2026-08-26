package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.service.PlatformPixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformPixServiceImpl implements PlatformPixService {

    private final AsaasPixClient asaasPixClient;
    private final AsaasProperties asaasProperties;

    @Override
    public List<PlatformPixKeyResponse> listKeys() {
        String masterKey = requireMasterApiKey();
        AsaasListResponse<AsaasPixKeyResponse> response = asaasPixClient.listPixKeys(masterKey);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData().stream().map(this::toResponse).toList();
    }

    @Override
    public PlatformPixKeyResponse createKey(CreatePlatformPixKeyRequest request) {
        request.getType().requireCreatableViaProvider();
        String masterKey = requireMasterApiKey();
        AsaasPixKeyResponse created = asaasPixClient.createPixKey(
                masterKey,
                AsaasPixKeyRequest.builder().type(PixKeyType.EVP.name()).build());
        log.info("Created Platform Account PIX key in Asaas: id={}", created.getId());
        return toResponse(created);
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

    private String requireMasterApiKey() {
        String key = asaasProperties.getKey();
        if (key == null || key.isBlank()) {
            throw new InvalidRequestException("ASAAS_API_KEY is not configured");
        }
        return key;
    }

    private PlatformPixKeyResponse toResponse(AsaasPixKeyResponse asaas) {
        PixKeyType type = parseType(asaas.getType() != null ? asaas.getType() : asaas.getPixKeyType());
        return PlatformPixKeyResponse.builder()
                .id(asaas.getId())
                .type(type)
                .key(asaas.getKey())
                .status(asaas.getStatus())
                .build();
    }

    private static PixKeyType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PixKeyType.EVP;
        }
        try {
            return PixKeyType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PixKeyType.EVP;
        }
    }
}
