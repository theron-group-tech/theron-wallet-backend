package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.*;
import com.theron.wallet.dto.request.CreatePixKeyRequest;
import com.theron.wallet.dto.request.CreatePixStaticQrCodeRequest;
import com.theron.wallet.dto.response.PixKeyResponse;
import com.theron.wallet.dto.response.PixStaticQrCodeResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.exception.SubaccountOperationBlockedException;
import com.theron.wallet.integration.AsaasPixClient;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.PixKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PixKeyServiceImpl implements PixKeyService {

    private static final Set<SubaccountStatus> ALLOWED_STATUSES =
            Set.of(SubaccountStatus.ACTIVE);

    private final SubaccountRepository subaccountRepository;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasPixClient asaasPixClient;

    @Override
    @Transactional(readOnly = true)
    public PixKeyResponse createPixKey(UUID subaccountId, CreatePixKeyRequest request) {
        log.info("Creating PIX key: subaccountId={}, type={}", subaccountId, request.getType());
        request.getType().requireCreatableViaProvider();

        String apiKey = resolveApiKey(subaccountId);
        AsaasPixKeyResponse response = asaasPixClient.createPixKey(apiKey,
                AsaasPixKeyRequest.builder().type(request.getType().name()).build());

        log.info("PIX key created: subaccountId={}, pixKeyId={}", subaccountId, response.getId());
        return toPixKeyResponse(response);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PixKeyResponse> listPixKeys(UUID subaccountId) {
        log.info("Listing PIX keys: subaccountId={}", subaccountId);

        String apiKey = resolveApiKey(subaccountId);
        AsaasListResponse<AsaasPixKeyResponse> response = asaasPixClient.listPixKeys(apiKey);

        List<AsaasPixKeyResponse> data = response.getData() != null ? response.getData() : List.of();
        return data.stream().map(this::toPixKeyResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void deletePixKey(UUID subaccountId, String pixKeyId) {
        log.info("Deleting PIX key: subaccountId={}, pixKeyId={}", subaccountId, pixKeyId);

        String apiKey = resolveApiKey(subaccountId);
        asaasPixClient.deletePixKey(apiKey, pixKeyId);

        log.info("PIX key deleted: subaccountId={}, pixKeyId={}", subaccountId, pixKeyId);
    }

    @Override
    @Transactional(readOnly = true)
    public PixStaticQrCodeResponse createStaticQrCode(
            UUID subaccountId, String pixKeyId, CreatePixStaticQrCodeRequest request) {
        log.info("Creating static PIX QR code: subaccountId={}, pixKeyId={}", subaccountId, pixKeyId);

        String apiKey = resolveApiKey(subaccountId);
        AsaasPixStaticQrCodeRequest qrRequest = AsaasPixStaticQrCodeRequest.builder()
                .value(request.getValue())
                .description(request.getDescription())
                .format("ALL")
                .build();

        AsaasPixStaticQrCodeResponse response = asaasPixClient.createStaticQrCode(apiKey, pixKeyId, qrRequest);

        log.info("Static PIX QR code created: subaccountId={}, qrCodeId={}", subaccountId, response.getId());
        return toQrCodeResponse(response);
    }

    @Override
    @Transactional(readOnly = true)
    public void deleteStaticQrCode(UUID subaccountId, String qrCodeId) {
        log.info("Deleting static PIX QR code: subaccountId={}, qrCodeId={}", subaccountId, qrCodeId);

        String apiKey = resolveApiKey(subaccountId);
        asaasPixClient.deleteStaticQrCode(apiKey, qrCodeId);

        log.info("Static PIX QR code deleted: subaccountId={}, qrCodeId={}", subaccountId, qrCodeId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveApiKey(UUID subaccountId) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));

        if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
            throw new SubaccountOperationBlockedException(
                    "Subaccount is not eligible for PIX operations. Current status: " + subaccount.getStatus());
        }

        return asaasApiKeyResolver.resolveForSubaccount(subaccountId);
    }

    private PixKeyResponse toPixKeyResponse(AsaasPixKeyResponse r) {
        return PixKeyResponse.builder()
                .id(r.getId())
                .key(r.getKey())
                .type(r.getType())
                .status(r.getStatus())
                .canBeDeleted(r.getCanBeDeleted())
                .dateCreated(r.getDateCreated())
                .build();
    }

    private PixStaticQrCodeResponse toQrCodeResponse(AsaasPixStaticQrCodeResponse r) {
        return PixStaticQrCodeResponse.builder()
                .id(r.getId())
                .addressKey(r.getAddressKey())
                .description(r.getDescription())
                .payload(r.getPayload())
                .encodedImage(r.getEncodedImage())
                .expirationDate(r.getExpirationDate())
                .allowsMultiplePayments(r.getAllowsMultiplePayments())
                .value(r.getValue())
                .build();
    }
}
