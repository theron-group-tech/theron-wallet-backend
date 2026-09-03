package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Canonical Asaas webhook registration for Theron subaccounts
 * (inline on create + admin repair via {@link AsaasWebhookClient}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasWebhookConfigFactory {

    private static final AtomicBoolean WEBHOOK_SKIP_LOGGED = new AtomicBoolean(false);

    private static final List<String> ACCOUNT_STATUS_WEBHOOK_EVENTS = List.of(
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_APPROVED",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_PENDING",
            "ACCOUNT_STATUS_BANK_ACCOUNT_INFO_REJECTED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_APPROVED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_EXPIRED",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_EXPIRING_SOON",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_PENDING",
            "ACCOUNT_STATUS_COMMERCIAL_INFO_REJECTED",
            "ACCOUNT_STATUS_DOCUMENT_APPROVED",
            "ACCOUNT_STATUS_DOCUMENT_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_DOCUMENT_PENDING",
            "ACCOUNT_STATUS_DOCUMENT_REJECTED",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_APPROVED",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_AWAITING_APPROVAL",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_PENDING",
            "ACCOUNT_STATUS_GENERAL_APPROVAL_REJECTED");

    private static final List<String> WEBHOOK_EVENTS = Stream.concat(
            Stream.of(
                    "PAYMENT_CONFIRMED",
                    "PAYMENT_RECEIVED",
                    "PAYMENT_OVERDUE",
                    "PAYMENT_DELETED",
                    "PAYMENT_REFUNDED",
                    "PAYMENT_UPDATED",
                    "PAYMENT_CHARGEBACK_REQUESTED",
                    "PAYMENT_CHARGEBACK_DISPUTE",
                    "PAYMENT_AWAITING_CHARGEBACK_REVERSAL",
                    "TRANSFER_CREATED",
                    "TRANSFER_PENDING",
                    "TRANSFER_IN_BANK_PROCESSING",
                    "TRANSFER_BLOCKED",
                    "TRANSFER_DONE",
                    "TRANSFER_FAILED",
                    "TRANSFER_CANCELLED"),
            ACCOUNT_STATUS_WEBHOOK_EVENTS.stream()).toList();

    private final AsaasProperties asaasProperties;

    /**
     * @return singleton list for inline create, or {@code null} when {@code ASAAS_WEBHOOK_URL} is unset
     */
    public List<AsaasWebhookConfigRequest> buildInlineConfigs(String webhookToken) {
        AsaasWebhookConfigRequest config = buildConfig(webhookToken);
        return config == null ? null : List.of(config);
    }

    /**
     * @return webhook body for POST /webhooks, or {@code null} when URL is unset
     */
    public AsaasWebhookConfigRequest buildConfig(String webhookToken) {
        String webhookUrl = asaasProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            if (WEBHOOK_SKIP_LOGGED.compareAndSet(false, true)) {
                log.info("ASAAS_WEBHOOK_URL not set — subaccounts created/repaired without Asaas webhooks");
            }
            return null;
        }
        if (webhookToken == null || webhookToken.isBlank()) {
            throw new IllegalArgumentException("webhookToken is required to register Asaas webhooks");
        }
        return AsaasWebhookConfigRequest.builder()
                .name("Theron Wallet")
                .url(webhookUrl)
                .email("webhooks@theron.internal")
                .enabled(true)
                .interrupted(false)
                .apiVersion("3")
                .authToken(webhookToken)
                .sendType("SEQUENTIALLY")
                .events(WEBHOOK_EVENTS)
                .build();
    }

    public boolean isWebhookUrlConfigured() {
        String webhookUrl = asaasProperties.getWebhookUrl();
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
