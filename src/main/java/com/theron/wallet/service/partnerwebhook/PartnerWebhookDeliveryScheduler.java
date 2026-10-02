package com.theron.wallet.service.partnerwebhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerWebhookDeliveryScheduler {

    private final PartnerWebhookDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${platform.partner-webhook.delivery-fixed-delay-ms:5000}")
    public void pollAndDeliver() {
        try {
            deliveryService.deliverDueBatch();
        } catch (RuntimeException ex) {
            log.warn("Partner webhook delivery poll failed: {}", ex.getMessage());
        }
    }
}
