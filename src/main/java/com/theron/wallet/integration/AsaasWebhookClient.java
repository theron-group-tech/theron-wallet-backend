package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Asaas webhook configuration client.
 * This client uses PER-SUBACCOUNT API keys (not the root key)
 * to register webhooks under each subaccount's context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasWebhookClient {

    private final AsaasHttpGateway asaasHttpGateway;

    public AsaasWebhookConfigResponse createWebhook(String subaccountApiKey, AsaasWebhookConfigRequest request) {
        log.info("Creating webhook in Asaas for subaccount: url={}", request.getUrl());
        return asaasHttpGateway.post(subaccountApiKey, "/webhooks", request, AsaasWebhookConfigResponse.class);
    }
}
