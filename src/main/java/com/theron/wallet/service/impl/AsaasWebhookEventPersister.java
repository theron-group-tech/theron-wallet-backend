package com.theron.wallet.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.AsaasWebhookEvent;
import com.theron.wallet.enums.AsaasWebhookEventStatus;
import com.theron.wallet.repository.AsaasWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookEventPersister {

    private final AsaasWebhookEventRepository asaasWebhookEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AsaasWebhookEvent> claim(String eventId, AsaasWebhookPayload payload) {
        Optional<AsaasWebhookEvent> existing = asaasWebhookEventRepository.findByAsaasEventId(eventId);
        if (existing.isPresent()) {
            AsaasWebhookEventStatus status = existing.get().getStatus();
            if (status == AsaasWebhookEventStatus.PROCESSED || status == AsaasWebhookEventStatus.IGNORED) {
                return Optional.empty();
            }
            return existing;
        }
        try {
            AsaasWebhookEvent event = AsaasWebhookEvent.builder()
                    .asaasEventId(eventId)
                    .event(payload.getEvent())
                    .resourceId(resourceId(payload))
                    .status(AsaasWebhookEventStatus.RECEIVED)
                    .payload(toPayloadMap(payload))
                    .build();
            return Optional.of(asaasWebhookEventRepository.saveAndFlush(event));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate Asaas webhook event skipped: eventId={}", eventId);
            return asaasWebhookEventRepository.findByAsaasEventId(eventId)
                    .filter(event -> event.getStatus() != AsaasWebhookEventStatus.PROCESSED
                            && event.getStatus() != AsaasWebhookEventStatus.IGNORED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mark(AsaasWebhookEvent event, AsaasWebhookEventStatus status) {
        asaasWebhookEventRepository.findById(event.getId()).ifPresent(persisted -> {
            persisted.setStatus(status);
            if (status == AsaasWebhookEventStatus.PROCESSED || status == AsaasWebhookEventStatus.IGNORED) {
                persisted.setProcessedAt(java.time.LocalDateTime.now());
            }
            asaasWebhookEventRepository.save(persisted);
        });
    }

    private Map<String, Object> toPayloadMap(AsaasWebhookPayload payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {});
    }

    static String resourceId(AsaasWebhookPayload payload) {
        if (payload.getPayment() != null && payload.getPayment().getId() != null) {
            return payload.getPayment().getId();
        }
        if (payload.getTransfer() != null && payload.getTransfer().getId() != null) {
            return payload.getTransfer().getId();
        }
        return null;
    }
}
