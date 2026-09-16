package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasPixQrCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Asaas payment client with per-request API key support for tenant-aware operations.
 * Each method accepts an API key that determines the Asaas account context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasPaymentClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasPaymentResponse createPayment(String apiKey, AsaasPaymentRequest request) {
        return createPayment(apiKey, request, request != null ? request.getExternalReference() : null);
    }

    public AsaasPaymentResponse createPayment(String apiKey, AsaasPaymentRequest request, String idempotencyKey) {
        log.info("Creating PIX payment in Asaas: customer={}, value={}",
                request.getCustomer(), request.getValue());
        return asaasHttpGateway.postFinancial(apiKey, "/payments", request, idempotencyKey, AsaasPaymentResponse.class);
    }

    public AsaasPaymentResponse retrievePayment(String apiKey, String paymentId) {
        log.info("Retrieving payment from Asaas: id={}", paymentId);
        return asaasHttpGateway.get(apiKey, "/payments/{id}", AsaasPaymentResponse.class, paymentId);
    }

    public AsaasListResponse<AsaasPaymentResponse> listPayments(
            String apiKey, String status, int offset, int limit) {
        log.info("Listing payments in Asaas: status={}, offset={}, limit={}", status, offset, limit);
        return asaasHttpGateway.get(
                apiKey,
                "/payments?status={status}&offset={offset}&limit={limit}",
                new ParameterizedTypeReference<AsaasListResponse<AsaasPaymentResponse>>() {},
                status,
                offset,
                limit);
    }

    public AsaasPixQrCodeResponse getPixQrCode(String apiKey, String paymentId) {
        log.info("Retrieving PIX QR code from Asaas: paymentId={}", paymentId);
        return asaasHttpGateway.get(apiKey, "/payments/{id}/pixQrCode", AsaasPixQrCodeResponse.class, paymentId);
    }

    public void deletePayment(String apiKey, String paymentId) {
        log.info("Deleting payment in Asaas: id={}", paymentId);
        asaasHttpGateway.delete(apiKey, "/payments/{id}", paymentId);
    }
}
