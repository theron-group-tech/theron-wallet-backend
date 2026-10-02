package com.theron.wallet.service;

import java.util.Map;
import java.util.UUID;

public interface PartnerWebhookOutboxService {

    /**
     * Enqueues an outbound partner webhook when the organization is Vem Comigo and
     * webhook URL/secret are configured. No-op otherwise. Dedupes by {@code idempotencyKey}.
     */
    void enqueueIfVemComigo(
            UUID organizationId,
            String eventType,
            String idempotencyKey,
            Map<String, Object> data);
}
