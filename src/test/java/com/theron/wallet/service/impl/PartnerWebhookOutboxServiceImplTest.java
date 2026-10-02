package com.theron.wallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.config.VemComigoSplitProperties;
import com.theron.wallet.entity.PartnerWebhookOutbox;
import com.theron.wallet.enums.PartnerWebhookEventTypes;
import com.theron.wallet.enums.PartnerWebhookOutboxStatus;
import com.theron.wallet.repository.PartnerWebhookOutboxRepository;
import com.theron.wallet.service.partnerwebhook.PartnerWebhookSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerWebhookOutboxServiceImplTest {

    private static final UUID VEM_COMIGO =
            UUID.fromString("565d0a47-6cd8-441a-b1de-766f042cd6d5");

    @Mock private PartnerWebhookOutboxRepository outboxRepository;
    @Mock private VemComigoSplitProperties vemComigoSplitProperties;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private PartnerWebhookOutboxServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        when(vemComigoSplitProperties.getOrganizationId()).thenReturn(VEM_COMIGO);
        org.mockito.Mockito.lenient().when(vemComigoSplitProperties.isWebhookEnabled()).thenReturn(true);
        org.mockito.Mockito.lenient().when(vemComigoSplitProperties.getWebhookUrl())
                .thenReturn("https://partner.example/webhooks");
        org.mockito.Mockito.lenient().when(vemComigoSplitProperties.getWebhookSecret())
                .thenReturn("secret");
        org.mockito.Mockito.lenient().when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"event\":\"test\"}");
    }

    @Test
    @DisplayName("ignores non-Vem Comigo organization")
    void ignoresOtherOrg() {
        service.enqueueIfVemComigo(
                UUID.randomUUID(),
                PartnerWebhookEventTypes.CHARGE_RECEIVED,
                "k1",
                Map.of("amount", "10"));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("enqueues when Vem Comigo and URL/secret configured")
    void enqueuesForVemComigo() {
        when(outboxRepository.findByIdempotencyKey("k1")).thenReturn(Optional.empty());
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.enqueueIfVemComigo(
                VEM_COMIGO,
                PartnerWebhookEventTypes.CHARGE_RECEIVED,
                "k1",
                Map.of("amount", "10.00"));

        ArgumentCaptor<PartnerWebhookOutbox> captor = ArgumentCaptor.forClass(PartnerWebhookOutbox.class);
        verify(outboxRepository).save(captor.capture());
        PartnerWebhookOutbox row = captor.getValue();
        assertThat(row.getOrganizationId()).isEqualTo(VEM_COMIGO);
        assertThat(row.getEventType()).isEqualTo(PartnerWebhookEventTypes.CHARGE_RECEIVED);
        assertThat(row.getIdempotencyKey()).isEqualTo("k1");
        assertThat(row.getStatus()).isEqualTo(PartnerWebhookOutboxStatus.PENDING);
    }

    @Test
    @DisplayName("dedupes by idempotency key")
    void dedupesIdempotencyKey() {
        when(outboxRepository.findByIdempotencyKey("k1"))
                .thenReturn(Optional.of(PartnerWebhookOutbox.builder().idempotencyKey("k1").build()));

        service.enqueueIfVemComigo(
                VEM_COMIGO,
                PartnerWebhookEventTypes.CHARGE_RECEIVED,
                "k1",
                Map.of());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("skips when webhook URL missing")
    void skipsWithoutUrl() {
        when(vemComigoSplitProperties.getWebhookUrl()).thenReturn("");
        service.enqueueIfVemComigo(
                VEM_COMIGO,
                PartnerWebhookEventTypes.PIX_INBOUND_RECEIVED,
                "k2",
                Map.of());
        verify(outboxRepository, never()).save(any());
    }
}

@ExtendWith(MockitoExtension.class)
class PartnerWebhookSignerTest {

    @Test
    void signsBodyWithSha256Prefix() {
        String sig = PartnerWebhookSigner.sign("secret", "{\"a\":1}");
        assertThat(sig).startsWith("sha256=");
        assertThat(sig.length()).isGreaterThan(10);
        assertThat(PartnerWebhookSigner.sign("secret", "{\"a\":1}"))
                .isEqualTo(sig);
        assertThat(PartnerWebhookSigner.sign("secret", "{\"a\":2}"))
                .isNotEqualTo(sig);
    }
}

@ExtendWith(MockitoExtension.class)
class PartnerWebhookDeliveryBackoffTest {

    @Test
    void backoffCapsAtFifteenMinutes() {
        assertThat(com.theron.wallet.service.partnerwebhook.PartnerWebhookDeliveryService.backoffSeconds(1))
                .isEqualTo(30);
        assertThat(com.theron.wallet.service.partnerwebhook.PartnerWebhookDeliveryService.backoffSeconds(2))
                .isEqualTo(60);
        assertThat(com.theron.wallet.service.partnerwebhook.PartnerWebhookDeliveryService.backoffSeconds(20))
                .isEqualTo(900);
    }
}
