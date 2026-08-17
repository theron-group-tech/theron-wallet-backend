package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Asaas subaccount (conta-filha) management.
 * Uses the platform root key since subaccount creation requires the parent account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasSubaccountClient {

    private final AsaasHttpGateway asaasHttpGateway;
    private final AsaasProperties asaasProperties;

    public AsaasSubaccountResponse createSubaccount(AsaasSubaccountRequest request) {
        log.info("Creating subaccount in Asaas: cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));
        return asaasHttpGateway.post(
                asaasProperties.getKey(), "/accounts", request, AsaasSubaccountResponse.class);
    }

    public AsaasSubaccountResponse retrieveSubaccount(String asaasAccountId) {
        log.info("Retrieving subaccount from Asaas: id={}", asaasAccountId);
        return asaasHttpGateway.get(
                asaasProperties.getKey(), "/accounts/{id}", AsaasSubaccountResponse.class, asaasAccountId);
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) {
            return "****";
        }
        return cpfCnpj.substring(0, cpfCnpj.length() - 4) + "****";
    }
}
