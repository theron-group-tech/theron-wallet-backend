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
        return gateway.get(asaasProperties.getKey(), "/finance/balance", AsaasFinanceBalanceResponse.class);
    }
}
