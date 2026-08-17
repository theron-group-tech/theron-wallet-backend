package com.theron.wallet.controller;

import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Asaas payment webhook receiver")
public class WebhookController {

    private static final String ASAAS_ACCESS_TOKEN_HEADER = "asaas-access-token";

    private final WebhookService webhookService;

    @PostMapping("/asaas")
    @Operation(summary = "Receive Asaas payment webhook",
            description = "Validates the webhook token and processes payment events (confirmation, cancellation, etc.)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook processed or duplicate ignored"),
            @ApiResponse(responseCode = "401", description = "Invalid webhook token"),
            @ApiResponse(responseCode = "500", description = "Processing failed after the event was stored")
    })
    public ResponseEntity<Void> receivePaymentWebhook(
            @RequestHeader(value = ASAAS_ACCESS_TOKEN_HEADER, required = false) String webhookToken,
            @RequestBody AsaasWebhookPayload payload) {

        log.info("Webhook received: event={}", payload.getEvent());
        webhookService.receive(webhookToken, payload);
        return ResponseEntity.ok().build();
    }
}
