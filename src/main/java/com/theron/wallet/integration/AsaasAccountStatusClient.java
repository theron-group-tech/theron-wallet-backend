package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasAccountStatusResponse;
import com.theron.wallet.dto.asaas.AsaasDocumentItem;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Account-level Asaas endpoints authenticated with the <strong>subaccount</strong> API key
 * (e.g. {@code GET /myAccount/status}, {@code GET /myAccount/documents}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasAccountStatusClient {

    private static final ParameterizedTypeReference<AsaasListResponse<AsaasDocumentItem>> DOCUMENTS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasAccountStatusResponse getStatus(String subaccountApiKey) {
        log.info("Fetching Asaas myAccount status for subaccount");
        return asaasHttpGateway.get(subaccountApiKey, "/myAccount/status", AsaasAccountStatusResponse.class);
    }

    public List<AsaasDocumentItem> listDocuments(String subaccountApiKey) {
        log.info("Fetching Asaas myAccount documents for subaccount");
        AsaasListResponse<AsaasDocumentItem> response =
                asaasHttpGateway.get(subaccountApiKey, "/myAccount/documents", DOCUMENTS_TYPE);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }

    public String firstOnboardingUrl(String subaccountApiKey) {
        return listDocuments(subaccountApiKey).stream()
                .map(AsaasDocumentItem::getOnboardingUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}
