package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasAnticipationRequest;
import com.theron.wallet.dto.asaas.AsaasAnticipationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasAnticipationClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasAnticipationResponse simulate(String apiKey, AsaasAnticipationRequest request) {
        log.info("Simulating Asaas anticipation: payments={}",
                request != null && request.getPayment() != null ? request.getPayment().size() : 0);
        return asaasHttpGateway.post(apiKey, "/anticipations/simulate", request, AsaasAnticipationResponse.class);
    }

    public AsaasAnticipationResponse create(String apiKey, AsaasAnticipationRequest request) {
        log.info("Creating Asaas anticipation: payments={}",
                request != null && request.getPayment() != null ? request.getPayment().size() : 0);
        return asaasHttpGateway.postFinancial(
                apiKey, "/anticipations", request, null, AsaasAnticipationResponse.class);
    }

    public AsaasAnticipationResponse retrieve(String apiKey, String anticipationId) {
        return asaasHttpGateway.get(apiKey, "/anticipations/{id}", AsaasAnticipationResponse.class, anticipationId);
    }
}
