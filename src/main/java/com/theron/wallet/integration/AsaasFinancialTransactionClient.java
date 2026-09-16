package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasFinancialTransactionResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasFinancialTransactionClient {

    private static final ParameterizedTypeReference<AsaasListResponse<AsaasFinancialTransactionResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasListResponse<AsaasFinancialTransactionResponse> list(
            String apiKey,
            String startDate,
            String finishDate,
            int offset,
            int limit) {
        log.debug(
                "Listing Asaas financial transactions: startDate={}, finishDate={}, offset={}, limit={}",
                startDate,
                finishDate,
                offset,
                limit);

        return asaasHttpGateway.get(
                apiKey,
                "/financialTransactions?startDate={startDate}&finishDate={finishDate}&offset={offset}&limit={limit}&order=desc",
                RESPONSE_TYPE,
                startDate,
                finishDate,
                offset,
                limit);
    }
}
