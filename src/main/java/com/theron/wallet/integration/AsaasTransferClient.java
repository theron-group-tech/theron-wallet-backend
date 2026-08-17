package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasTransferClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasTransferResponse createTransfer(String apiKey, AsaasTransferRequest request) {
        return createTransfer(apiKey, request, request != null ? request.getExternalReference() : null);
    }

    public AsaasTransferResponse createTransfer(String apiKey, AsaasTransferRequest request, String idempotencyKey) {
        log.info("Creating transfer in Asaas: value={}, pixKeyType={}",
                request.getValue(), request.getPixAddressKeyType());
        return asaasHttpGateway.postFinancial(apiKey, "/transfers", request, idempotencyKey, AsaasTransferResponse.class);
    }

    public AsaasTransferResponse retrieveTransfer(String apiKey, String transferId) {
        log.info("Retrieving transfer from Asaas: id={}", transferId);
        return asaasHttpGateway.get(apiKey, "/transfers/{id}", AsaasTransferResponse.class, transferId);
    }
}
