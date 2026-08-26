package com.theron.wallet.controller;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasTransferValidationRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.InboundTransferService;
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

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Asaas payment and transfer webhook receivers")
public class WebhookController {

    private static final String ASAAS_ACCESS_TOKEN_HEADER = "asaas-access-token";

    private final WebhookService webhookService;
    private final InboundTransferService inboundTransferService;
    private final TransactionRepository transactionRepository;
    private final AsaasProperties asaasProperties;

    @PostMapping("/asaas")
    @Operation(summary = "Receive Asaas webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook processed or duplicate ignored"),
            @ApiResponse(responseCode = "401", description = "Invalid webhook token"),
            @ApiResponse(responseCode = "500", description = "Processing failed after the event was stored")
    })
    public ResponseEntity<Void> receiveAsaasWebhook(
            @RequestHeader(value = ASAAS_ACCESS_TOKEN_HEADER, required = false) String webhookToken,
            @RequestBody AsaasWebhookPayload payload) {

        log.info("Webhook received: event={}", payload.getEvent());
        if (isUnregisteredTransferEvent(payload)) {
            inboundTransferService.receive(webhookToken, payload);
        } else {
            webhookService.receive(webhookToken, payload);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/asaas/transfer-validation")
    @Operation(summary = "Validate an Asaas outgoing transfer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "APPROVED or REFUSED decision returned to Asaas"),
            @ApiResponse(responseCode = "401", description = "Invalid validation webhook token")
    })
    public ResponseEntity<TransferValidationResponse> validateTransfer(
            @RequestHeader(value = ASAAS_ACCESS_TOKEN_HEADER, required = false) String webhookToken,
            @RequestBody AsaasTransferValidationRequest payload) {

        if (!tokenMatches(asaasProperties.getTransferValidationToken(), webhookToken)) {
            return ResponseEntity.status(401).build();
        }
        if (payload == null || !"TRANSFER".equalsIgnoreCase(payload.getType()) || payload.getTransfer() == null) {
            return ResponseEntity.ok(TransferValidationResponse.refused("Operação de transferência inválida"));
        }

        String transferId = payload.getTransfer().getId();
        if (transferId == null || transferId.isBlank()) {
            return ResponseEntity.ok(TransferValidationResponse.refused("Transferência sem ID"));
        }

        var transaction = transactionRepository.findByAsaasPaymentId(transferId);
        if (transaction.isEmpty()) {
            return ResponseEntity.ok(TransferValidationResponse.refused("Transferência não encontrada no Theron"));
        }

        var tx = transaction.get();
        BigDecimal providerValue = payload.getTransfer().getValue();
        if (providerValue == null || tx.getAmount() == null || tx.getAmount().compareTo(providerValue) != 0) {
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Valor da transferência não corresponde ao registrado no Theron"));
        }
        if (tx.getStatus() != TransactionStatus.PROCESSING && tx.getStatus() != TransactionStatus.PENDING) {
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Transferência não está em estado elegível para autorização"));
        }

        return ResponseEntity.ok(TransferValidationResponse.approved());
    }

    private boolean isUnregisteredTransferEvent(AsaasWebhookPayload payload) {
        if (payload == null || payload.getEvent() == null || !payload.getEvent().startsWith("TRANSFER_")) {
            return false;
        }
        String transferId = payload.getTransfer() != null ? payload.getTransfer().getId() : null;
        return transferId == null || transactionRepository.findByAsaasPaymentId(transferId).isEmpty();
    }

    private static boolean tokenMatches(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null || actual.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record TransferValidationResponse(String status, String refuseReason) {
        static TransferValidationResponse approved() {
            return new TransferValidationResponse("APPROVED", null);
        }

        static TransferValidationResponse refused(String reason) {
            return new TransferValidationResponse("REFUSED", reason);
        }
    }
}
