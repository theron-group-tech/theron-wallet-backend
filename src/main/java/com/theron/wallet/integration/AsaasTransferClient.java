package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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

    public AsaasListResponse<AsaasTransferResponse> listTransfers(String apiKey, int offset, int limit) {
        log.info("Listing transfers in Asaas: offset={}, limit={}", offset, limit);
        return asaasHttpGateway.get(
                apiKey,
                "/transfers?offset={offset}&limit={limit}",
                new ParameterizedTypeReference<AsaasListResponse<AsaasTransferResponse>>() {},
                offset,
                limit);
    }
}
