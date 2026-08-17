package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasCreateCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates and manages Asaas customers within a subaccount's context.
 * Each call uses the subaccount's own API key — never the platform root key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasCustomerClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasCreateCustomerResponse createCustomer(String apiKey, AsaasCreateCustomerRequest request) {
        log.info("Creating Asaas customer: cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));
        return asaasHttpGateway.post(apiKey, "/customers", request, AsaasCreateCustomerResponse.class);
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) {
            return "****";
        }
        return cpfCnpj.substring(0, cpfCnpj.length() - 4) + "****";
    }
}
