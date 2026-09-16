package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixExternalKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyRequest;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeRequest;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasPixTransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Asaas PIX client — gerenciamento de chaves Pix e QR Codes estáticos.
 * Operações usam a API key passada pelo caller (subconta ou Master).
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

    public AsaasPixExternalKeyResponse lookupExternalKey(String apiKey, String type, String key) {
        log.info("Looking up external PIX key in Asaas: type={}", type);
        String uri = UriComponentsBuilder
                .fromPath("/pix/addressKeys/external")
                .queryParam("type", type)
                .queryParam("key", key)
                .encode()
                .build()
                .toUriString();
        return asaasHttpGateway.get(apiKey, uri, AsaasPixExternalKeyResponse.class);
    }

    public void deletePixKey(String apiKey, String pixKeyId) {
        log.info("Deleting PIX key in Asaas: pixKeyId={}", pixKeyId);
        asaasHttpGateway.delete(apiKey, "/pix/addressKeys/{id}", pixKeyId);
    }

    public AsaasPixStaticQrCodeResponse createStaticQrCode(
            String apiKey, String addressKey, AsaasPixStaticQrCodeRequest request) {
        log.info("Creating static PIX QR code in Asaas: addressKey={}", addressKey);
        AsaasPixStaticQrCodeRequest body = request != null ? request : new AsaasPixStaticQrCodeRequest();
        body.setAddressKey(addressKey);
        if (body.getFormat() == null) {
            body.setFormat("ALL");
        }
        return asaasHttpGateway.post(
                apiKey,
                "/pix/qrCodes/static",
                body,
                AsaasPixStaticQrCodeResponse.class);
    }

    public AsaasPixPayQrCodeResponse payQrCode(
            String apiKey, AsaasPixPayQrCodeRequest request, String idempotencyKey) {
        log.info("Paying PIX QR code in Asaas: value={}", request != null ? request.getValue() : null);
        return asaasHttpGateway.postFinancial(
                apiKey,
                "/pix/qrCodes/pay",
                request,
                idempotencyKey,
                AsaasPixPayQrCodeResponse.class);
    }

    public AsaasPixTransactionResponse retrievePixTransaction(String apiKey, String transactionId) {
        log.info("Retrieving PIX transaction in Asaas: id={}", transactionId);
        return asaasHttpGateway.get(
                apiKey, "/pix/transactions/{id}", AsaasPixTransactionResponse.class, transactionId);
    }

    public void deleteStaticQrCode(String apiKey, String qrCodeId) {
        log.info("Deleting static PIX QR code in Asaas: qrCodeId={}", qrCodeId);
        asaasHttpGateway.delete(apiKey, "/pix/qrCodes/static/{id}", qrCodeId);
    }
}
