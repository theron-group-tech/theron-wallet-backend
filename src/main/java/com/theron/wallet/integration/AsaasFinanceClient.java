package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasFinanceBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AsaasFinanceClient {

    private final AsaasHttpGateway gateway;
    private final AsaasProperties asaasProperties;

    public AsaasFinanceBalanceResponse getMasterBalance() {
        return getBalance(asaasProperties.getKey());
    }

    public AsaasFinanceBalanceResponse getBalance(String apiKey) {
        return gateway.get(apiKey, "/finance/balance", AsaasFinanceBalanceResponse.class);
    }
}
