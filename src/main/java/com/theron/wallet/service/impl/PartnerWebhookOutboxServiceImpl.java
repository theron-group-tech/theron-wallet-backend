package com.theron.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.config.VemComigoSplitProperties;
import com.theron.wallet.entity.PartnerWebhookOutbox;
import com.theron.wallet.enums.PartnerWebhookOutboxStatus;
import com.theron.wallet.repository.PartnerWebhookOutboxRepository;
import com.theron.wallet.service.PartnerWebhookOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerWebhookOutboxServiceImpl implements PartnerWebhookOutboxService {

    private final PartnerWebhookOutboxRepository outboxRepository;
    private final VemComigoSplitProperties vemComigoSplitProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void enqueueIfVemComigo(
            UUID organizationId,
            String eventType,
            String idempotencyKey,
            Map<String, Object> data) {
        if (!isEligible(organizationId)) {
            return;
        }
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(idempotencyKey)) {
            log.warn("Partner webhook enqueue skipped: missing eventType or idempotencyKey");
            return;
        }
        if (outboxRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.debug("Partner webhook already enqueued: idempotencyKey={}", idempotencyKey);
            return;
        }

        UUID deliveryId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", deliveryId.toString());
        envelope.put("event", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("organizationId", organizationId.toString());
        envelope.put("data", data != null ? data : Map.of());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize partner webhook payload", ex);
        }

        PartnerWebhookOutbox row = PartnerWebhookOutbox.builder()
                .id(deliveryId)
                .organizationId(organizationId)
                .eventType(eventType)
                .idempotencyKey(idempotencyKey.trim())
                .payloadJson(payloadJson)
                .status(PartnerWebhookOutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        try {
            outboxRepository.save(row);
            log.info("Partner webhook enqueued: event={}, orgId={}, idempotencyKey={}",
                    eventType, organizationId, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Partner webhook race on idempotencyKey={}: {}", idempotencyKey, ex.getMessage());
        }
    }

    private boolean isEligible(UUID organizationId) {
        if (organizationId == null) {
            return false;
        }
        UUID configured = vemComigoSplitProperties.getOrganizationId();
        if (configured == null || !configured.equals(organizationId)) {
            return false;
        }
        if (!vemComigoSplitProperties.isWebhookEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(vemComigoSplitProperties.getWebhookUrl())
                || !StringUtils.hasText(vemComigoSplitProperties.getWebhookSecret())) {
            log.debug("Vem Comigo webhook skipped: URL or secret not configured");
            return false;
        }
        return true;
    }
}
