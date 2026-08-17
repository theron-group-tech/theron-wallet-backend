package com.theron.wallet.controller;

import com.theron.wallet.dto.request.InternalTransferRequest;
import com.theron.wallet.dto.response.InternalTransferResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.InternalTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Internal wallet-to-wallet transfer operations")
public class TransferController {

    public static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final InternalTransferService internalTransferService;
    private final ActorResolver actorResolver;

    @PostMapping("/internal")
    @Operation(
            summary = "Transferência interna entre carteiras",
            description = "Transfere saldo entre duas carteiras de subcontas da plataforma atomicamente, "
                    + "sem envolver provedor externo. Chave de idempotência garante exactly-once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferência concluída"),
            @ApiResponse(responseCode = "400", description = "Erro de validação ou auto-transferência"),
            @ApiResponse(responseCode = "404", description = "Subconta ou carteira não encontrada"),
            @ApiResponse(responseCode = "409", description = "Saldo insuficiente")
    })
    public ResponseEntity<InternalTransferResponse> internalTransfer(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InternalTransferRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setIdempotencyKey(idempotencyKey.trim());
        }
        InternalTransferResponse response = internalTransferService.transfer(
                actorResolver.requireProductUserId(actorUserId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
