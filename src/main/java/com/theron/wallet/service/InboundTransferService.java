package com.theron.wallet.service;

import com.theron.wallet.dto.asaas.AsaasWebhookPayload;

public interface InboundTransferService {
    boolean canHandle(String token, AsaasWebhookPayload payload);
    void receive(String token, AsaasWebhookPayload payload);
}
