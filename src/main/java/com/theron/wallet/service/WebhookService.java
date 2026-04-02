package com.theron.wallet.service;

import com.theron.wallet.dto.asaas.AsaasWebhookPayload;

public interface WebhookService {

    void processPaymentWebhook(AsaasWebhookPayload payload);

    void processTransferWebhook(AsaasWebhookPayload payload);
}
