package com.theron.wallet.controller;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasTransferValidationRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookPayload;
import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.entity.PixTransaction;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.service.InboundTransferService;
import com.theron.wallet.service.PlatformPixService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Asaas payment and transfer webhook receivers")
public class WebhookController {

    private static final String ASAAS_ACCESS_TOKEN_HEADER = "asaas-access-token";
    private static final String QR_PAY_DESTINATION_FALLBACK = "QR_CODE_PAY";

    private final WebhookService webhookService;
    private final InboundTransferService inboundTransferService;
    private final TransactionRepository transactionRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final PlatformPixService platformPixService;
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
            if (tryHandlePlatformMasterTransfer(webhookToken, payload)) {
                return ResponseEntity.ok().build();
            }
            inboundTransferService.receive(webhookToken, payload);
        } else {
            webhookService.receive(webhookToken, payload);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/asaas/transfer-validation")
    @Operation(
            summary = "Validate an Asaas outgoing transfer",
            description = "Asaas calls this before executing Master PIX outflows. "
                    + "Supported types: TRANSFER (key transfer, lookup by transfer.id) "
                    + "and PIX_QR_CODE (QR copia e cola pay, lookup by pixQrCode.id). "
                    + "Returns APPROVED or REFUSED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "APPROVED or REFUSED decision returned to Asaas"),
            @ApiResponse(responseCode = "401", description = "Invalid validation webhook token")
    })
    public ResponseEntity<TransferValidationResponse> validateTransfer(
            @RequestHeader(value = ASAAS_ACCESS_TOKEN_HEADER, required = false) String webhookToken,
            @RequestBody AsaasTransferValidationRequest payload) {

        if (!tokenMatches(asaasProperties.getTransferValidationToken(), webhookToken)) {
            log.warn("Transfer validation rejected: invalid token");
            return ResponseEntity.status(401).build();
        }
        if (payload == null || payload.getType() == null || payload.getType().isBlank()) {
            log.info("Transfer validation REFUSED: missing payload type");
            return ResponseEntity.ok(TransferValidationResponse.refused("Operação de transferência inválida"));
        }

        String type = payload.getType().trim().toUpperCase();
        log.info("Transfer validation request: type={}", type);

        if ("TRANSFER".equals(type)) {
            return validateTransferType(payload);
        }
        if ("PIX_QR_CODE".equals(type)) {
            return validatePixQrCodeType(payload);
        }

        log.info("Transfer validation REFUSED: unsupported type={}", type);
        return ResponseEntity.ok(TransferValidationResponse.refused("Tipo de operação não suportado"));
    }

    private ResponseEntity<TransferValidationResponse> validateTransferType(
            AsaasTransferValidationRequest payload) {
        if (payload.getTransfer() == null) {
            log.info("Transfer validation REFUSED: missing transfer object");
            return ResponseEntity.ok(TransferValidationResponse.refused("Operação de transferência inválida"));
        }

        String transferId = payload.getTransfer().getId();
        if (transferId == null || transferId.isBlank()) {
            log.info("Transfer validation REFUSED: missing transferId");
            return ResponseEntity.ok(TransferValidationResponse.refused("Transferência sem ID"));
        }

        BigDecimal providerValue = payload.getTransfer().getValue();
        String externalReference = payload.getTransfer().getExternalReference();
        log.info("Transfer validation TRANSFER: transferId={}, externalReference={}, value={}",
                transferId, externalReference, providerValue);

        var transaction = transactionRepository.findByAsaasPaymentId(transferId);
        if (transaction.isPresent()) {
            var tx = transaction.get();
            if (providerValue == null || tx.getAmount() == null || tx.getAmount().compareTo(providerValue) != 0) {
                log.info("Transfer validation REFUSED: subaccount amount mismatch transferId={}", transferId);
                return ResponseEntity.ok(TransferValidationResponse.refused(
                        "Valor da transferência não corresponde ao registrado no Theron"));
            }
            if (tx.getStatus() != TransactionStatus.PROCESSING && tx.getStatus() != TransactionStatus.PENDING) {
                log.info("Transfer validation REFUSED: subaccount invalid status transferId={}", transferId);
                return ResponseEntity.ok(TransferValidationResponse.refused(
                        "Transferência não está em estado elegível para autorização"));
            }
            log.info("Transfer validation APPROVED: subaccount transferId={}", transferId);
            return ResponseEntity.ok(TransferValidationResponse.approved());
        }

        Optional<PlatformPixTransfer> platformTransfer = platformPixService
                .bindAndFindPlatformTransferForValidation(transferId, externalReference, providerValue);
        if (platformTransfer.isPresent()) {
            return approvePlatformMasterTransfer(
                    platformTransfer.get(),
                    providerValue,
                    "transferId=" + transferId + ", idempotencyKey=" + platformTransfer.get().getIdempotencyKey());
        }

        log.info("Transfer validation REFUSED: not found transferId={}, externalReference={}",
                transferId, externalReference);
        return ResponseEntity.ok(TransferValidationResponse.refused("Transferência não encontrada no Theron"));
    }

    private ResponseEntity<TransferValidationResponse> validatePixQrCodeType(
            AsaasTransferValidationRequest payload) {
        AsaasTransferValidationRequest.PixQrCode pixQrCode = payload.getPixQrCode();
        if (pixQrCode == null) {
            log.info("Transfer validation REFUSED: missing pixQrCode object");
            return ResponseEntity.ok(TransferValidationResponse.refused("Operação de QR Code Pix inválida"));
        }

        String pixTransactionId = pixQrCode.getId();
        if (pixTransactionId == null || pixTransactionId.isBlank()) {
            log.info("Transfer validation REFUSED: missing pixQrCode id");
            return ResponseEntity.ok(TransferValidationResponse.refused("QR Code Pix sem ID"));
        }

        BigDecimal providerValue = pixQrCode.getValue();
        log.info("Transfer validation PIX_QR_CODE: pixTransactionId={}, value={}",
                pixTransactionId, providerValue);

        Optional<PlatformPixTransfer> platformTransfer = platformPixService
                .findPlatformPixQrPayForValidation(pixTransactionId, providerValue);
        if (platformTransfer.isPresent()) {
            return approvePlatformMasterTransfer(
                    platformTransfer.get(),
                    providerValue,
                    "pixTransactionId=" + pixTransactionId
                            + ", idempotencyKey=" + platformTransfer.get().getIdempotencyKey());
        }

        Optional<PixTransaction> subaccountPix = findSubaccountQrPayForValidation(
                pixTransactionId, providerValue);
        if (subaccountPix.isPresent()) {
            return approveSubaccountQrPay(
                    subaccountPix.get(),
                    providerValue,
                    "pixTransactionId=" + pixTransactionId
                            + ", transactionId=" + subaccountPix.get().getTransaction().getId());
        }

        log.info("Transfer validation REFUSED: QR pay not found pixTransactionId={}", pixTransactionId);
        return ResponseEntity.ok(TransferValidationResponse.refused("Pagamento QR Code Pix não encontrado no Theron"));
    }

    private ResponseEntity<TransferValidationResponse> approvePlatformMasterTransfer(
            PlatformPixTransfer row, BigDecimal providerValue, String logContext) {
        if (providerValue == null || row.getAmount() == null || row.getAmount().compareTo(providerValue) != 0) {
            log.info("Transfer validation REFUSED: master amount mismatch {}", logContext);
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Valor da transferência Master não corresponde ao registrado no Theron"));
        }
        if (row.getStatus() != TransactionStatus.PROCESSING && row.getStatus() != TransactionStatus.PENDING) {
            log.info("Transfer validation REFUSED: master invalid status {}", logContext);
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Transferência Master não está em estado elegível para autorização"));
        }
        log.info("Transfer validation APPROVED: master {}", logContext);
        return ResponseEntity.ok(TransferValidationResponse.approved());
    }

    private Optional<PixTransaction> findSubaccountQrPayForValidation(
            String pixTransactionId, BigDecimal amount) {
        if (pixTransactionId != null && !pixTransactionId.isBlank()) {
            Optional<PixTransaction> byPixTx =
                    pixTransactionRepository.findByAsaasPixTransactionIdWithTransaction(pixTransactionId.trim());
            if (byPixTx.isPresent()) {
                return byPixTx;
            }
        }
        if (amount != null) {
            LocalDateTime since = LocalDateTime.now().minusMinutes(10);
            List<PixTransaction> pending = pixTransactionRepository.findPendingQrPayForBind(
                    QR_PAY_DESTINATION_FALLBACK,
                    List.of(TransactionStatus.PROCESSING, TransactionStatus.PENDING),
                    amount,
                    since);
            if (!pending.isEmpty()) {
                return Optional.of(pending.getFirst());
            }
        }
        return Optional.empty();
    }

    private ResponseEntity<TransferValidationResponse> approveSubaccountQrPay(
            PixTransaction pixTransaction, BigDecimal providerValue, String logContext) {
        Transaction transaction = pixTransaction.getTransaction();
        if (providerValue == null || transaction.getAmount() == null
                || transaction.getAmount().compareTo(providerValue) != 0) {
            log.info("Transfer validation REFUSED: subaccount QR amount mismatch {}", logContext);
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Valor do QR Code Pix não corresponde ao registrado no Theron"));
        }
        if (transaction.getStatus() != TransactionStatus.PROCESSING
                && transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Transfer validation REFUSED: subaccount QR invalid status {}", logContext);
            return ResponseEntity.ok(TransferValidationResponse.refused(
                    "Pagamento QR Code Pix não está em estado elegível para autorização"));
        }
        log.info("Transfer validation APPROVED: subaccount {}", logContext);
        return ResponseEntity.ok(TransferValidationResponse.approved());
    }

    /**
     * Master Platform Account TRANSFER_* webhooks: no Subaccount row. Ack with global token
     * and update {@code platform_pix_transfer} when present — avoid 401 Unknown Asaas subaccount.
     */
    private boolean tryHandlePlatformMasterTransfer(String webhookToken, AsaasWebhookPayload payload) {
        if (!isGlobalWebhookToken(webhookToken)) {
            return false;
        }
        if (inboundTransferService.canHandle(webhookToken, payload)) {
            return false;
        }
        AsaasWebhookPayload.Transfer transfer = payload.getTransfer();
        String transferId = transfer != null ? transfer.getId() : null;
        String externalReference = transfer != null ? transfer.getExternalReference() : null;
        BigDecimal value = transfer != null ? transfer.getValue() : null;
        platformPixService.applyWebhookStatus(transferId, payload.getEvent(), externalReference, value);
        log.info("Ack Master/unbound Asaas TRANSFER webhook: event={}, transferId={}",
                payload.getEvent(), transferId);
        return true;
    }

    private boolean isUnregisteredTransferEvent(AsaasWebhookPayload payload) {
        if (payload == null || payload.getEvent() == null || !payload.getEvent().startsWith("TRANSFER_")) {
            return false;
        }
        String transferId = payload.getTransfer() != null ? payload.getTransfer().getId() : null;
        return transferId == null || transactionRepository.findByAsaasPaymentId(transferId).isEmpty();
    }

    private boolean isGlobalWebhookToken(String token) {
        return tokenMatches(asaasProperties.getWebhookToken(), token);
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
