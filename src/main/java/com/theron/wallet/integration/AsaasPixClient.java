package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Asaas PIX client — gerenciamento de chaves Pix e QR Codes estáticos.
 * Todas as operações usam a API key da subconta para isolamento de tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasPixClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasPixKeyResponse createPixKey(String apiKey, AsaasPixKeyRequest request) {
        log.info("Creating PIX key in Asaas: type={}", request.getType());
        return asaasHttpGateway.post(apiKey, "/pix/addressKeys", request, AsaasPixKeyResponse.class);
    }

    public AsaasListResponse<AsaasPixKeyResponse> listPixKeys(String apiKey) {
        log.info("Listing PIX keys in Asaas");
        return asaasHttpGateway.get(
                apiKey,
                "/pix/addressKeys",
                new ParameterizedTypeReference<AsaasListResponse<AsaasPixKeyResponse>>() {});
    }

    public void deletePixKey(String apiKey, String pixKeyId) {
        log.info("Deleting PIX key in Asaas: pixKeyId={}", pixKeyId);
        asaasHttpGateway.delete(apiKey, "/pix/addressKeys/{id}", pixKeyId);
    }

    public AsaasPixStaticQrCodeResponse createStaticQrCode(
            String apiKey, String pixKeyId, AsaasPixStaticQrCodeRequest request) {
        log.info("Creating static PIX QR code in Asaas: pixKeyId={}", pixKeyId);
        return asaasHttpGateway.post(
                apiKey,
                "/pix/addressKeys/{id}/qrCodes/static",
                request,
                AsaasPixStaticQrCodeResponse.class,
                pixKeyId);
    }

    public void deleteStaticQrCode(String apiKey, String qrCodeId) {
        log.info("Deleting static PIX QR code in Asaas: qrCodeId={}", qrCodeId);
        asaasHttpGateway.delete(apiKey, "/pix/qrCodes/static/{id}", qrCodeId);
    }
}
