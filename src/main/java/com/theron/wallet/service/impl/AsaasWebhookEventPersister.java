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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookEventPersister {

    private final AsaasWebhookEventRepository asaasWebhookEventRepository;
    private final ObjectMapper objectMapper;
    private final InboundPixDestinationResolver inboundPixDestinationResolver;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AsaasWebhookEvent> claim(String eventId, AsaasWebhookPayload payload) {
        Optional<AsaasWebhookEvent> existing = asaasWebhookEventRepository.findByAsaasEventId(eventId);
        if (existing.isPresent()) {
            AsaasWebhookEventStatus status = existing.get().getStatus();
            if (status == AsaasWebhookEventStatus.PROCESSED || status == AsaasWebhookEventStatus.IGNORED) {
                return Optional.empty();
            }
            if (markNonSubaccountInboundAsIgnored(existing.get(), payload)) {
                return Optional.empty();
            }
            resolveInboundDestination(payload);
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
            AsaasWebhookEvent saved = asaasWebhookEventRepository.saveAndFlush(event);
            if (markNonSubaccountInboundAsIgnored(saved, payload)) {
                return Optional.empty();
            }
            resolveInboundDestination(payload);
            return Optional.of(saved);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate Asaas webhook event skipped: eventId={}", eventId);
            return asaasWebhookEventRepository.findByAsaasEventId(eventId)
                    .filter(event -> event.getStatus() != AsaasWebhookEventStatus.PROCESSED
                            && event.getStatus() != AsaasWebhookEventStatus.IGNORED)
                    .map(event -> {
                        if (markNonSubaccountInboundAsIgnored(event, payload)) {
                            return null;
                        }
                        resolveInboundDestination(payload);
                        return event;
                    });
        }
    }

    /**
     * A PAYMENT_RECEIVED that resolves to PLATFORM or EXTERNAL does not belong
     * to an organization wallet, so WebhookServiceImpl must not attempt to
     * credit a Theron subaccount. Persist it as IGNORED and acknowledge it.
     * The platform Master balance is maintained by Asaas, while an unregistered
     * destination key is explicitly treated as external.
     */
    private boolean markNonSubaccountInboundAsIgnored(
            AsaasWebhookEvent event,
            AsaasWebhookPayload payload) {
        if (payload == null || payload.getPayment() == null || !"PAYMENT_RECEIVED".equals(payload.getEvent())) {
            return false;
        }

        try {
            InboundPixDestinationResolver.Resolution resolution =
                    inboundPixDestinationResolver.classify(payload);

            if (resolution.isSubaccount()) {
                return false;
            }

            event.setStatus(AsaasWebhookEventStatus.IGNORED);
            event.setProcessedAt(LocalDateTime.now());
            asaasWebhookEventRepository.save(event);

            if (resolution.isPlatform()) {
                log.info("Inbound Pix acknowledged without organization credit: destination=PLATFORM, paymentId={}, pixKey={}",
                        payload.getPayment().getId(), resolution.pixKey());
            } else {
                log.info("Inbound Pix acknowledged as external: destination=EXTERNAL, paymentId={}, pixKey={}",
                        payload.getPayment().getId(), resolution.pixKey());
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Could not classify inbound Pix destination; leaving webhook retryable: paymentId={}",
                    payload.getPayment().getId(), ex);
            return false;
        }
    }

    private void resolveInboundDestination(AsaasWebhookPayload payload) {
        if (payload == null || payload.getPayment() == null || !"PAYMENT_RECEIVED".equals(payload.getEvent())) {
            return;
        }

        try {
            InboundPixDestinationResolver.Resolution resolution =
                    inboundPixDestinationResolver.classify(payload);

            if (!resolution.isSubaccount() || resolution.subaccount() == null
                    || resolution.subaccount().getAsaasAccountId() == null
                    || resolution.subaccount().getAsaasAccountId().isBlank()) {
                return;
            }

            if (payload.getAccount() != null) {
                payload.getAccount().setId(resolution.subaccount().getAsaasAccountId());
            }
            log.info("Inbound Pix webhook destination resolved: event={}, paymentId={}, destination=SUBACCOUNT, subaccountId={}, asaasAccountId={}",
                    payload.getEvent(),
                    payload.getPayment().getId(),
                    resolution.subaccount().getId(),
                    resolution.subaccount().getAsaasAccountId());
        } catch (RuntimeException ex) {
            log.warn("Could not resolve inbound Pix webhook destination: paymentId={}",
                    payload.getPayment().getId(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mark(AsaasWebhookEvent event, AsaasWebhookEventStatus status) {
        asaasWebhookEventRepository.findById(event.getId()).ifPresent(persisted -> {
            persisted.setStatus(status);
            if (status == AsaasWebhookEventStatus.PROCESSED || status == AsaasWebhookEventStatus.IGNORED) {
                persisted.setProcessedAt(LocalDateTime.now());
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
