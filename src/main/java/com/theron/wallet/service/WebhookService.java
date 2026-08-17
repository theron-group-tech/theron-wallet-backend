package com.theron.wallet.service;

import com.theron.wallet.dto.asaas.AsaasWebhookPayload;

public interface WebhookService {

    void receive(String token, AsaasWebhookPayload payload);

    void processPaymentWebhook(AsaasWebhookPayload payload);

    void processTransferWebhook(AsaasWebhookPayload payload);
}
