package com.theron.wallet.controller;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Asaas payment webhook receiver")
public class WebhookController {

    private static final String ASAAS_ACCESS_TOKEN_HEADER = "asaas-access-token";

    private final WebhookService webhookService;
    private final AsaasProperties asaasProperties;

    @PostMapping("/asaas")
    @Operation(summary = "Receive Asaas payment webhook",
            description = "Validates the webhook token and processes payment events (confirmation, cancellation, etc.)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook processed"),
            @ApiResponse(responseCode = "401", description = "Invalid webhook token")
    })
    public ResponseEntity<Void> receivePaymentWebhook(
            @RequestHeader(value = ASAAS_ACCESS_TOKEN_HEADER, required = false) String webhookToken,
            @RequestBody AsaasWebhookPayload payload) {

        if (!isValidWebhookToken(webhookToken)) {
            log.warn("Webhook received with invalid token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Webhook received: event={}", payload.getEvent());

        try {
            if (payload.getEvent() != null && payload.getEvent().startsWith("TRANSFER_")) {
                webhookService.processTransferWebhook(payload);
            } else {
                webhookService.processPaymentWebhook(payload);
            }
        } catch (Exception ex) {
            log.error("Error processing webhook: event={}, error={}", payload.getEvent(), ex.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    private boolean isValidWebhookToken(String token) {
        String expectedToken = asaasProperties.getWebhookToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            log.warn("Webhook token not configured — accepting all webhooks (development mode)");
            return true;
        }
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
