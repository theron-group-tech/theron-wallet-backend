package com.theron.wallet.controller;

import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposits", description = "PIX deposit creation and QR code retrieval")
public class DepositController {

    private final DepositService depositService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @PostMapping
    @Operation(summary = "Criar depósito via Pix",
            description = "Cria uma cobrança Pix na subconta via Asaas. Use o transactionId retornado para buscar o QR Code.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Depósito criado, aguardando pagamento Pix"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "404", description = "Subconta não encontrada"),
            @ApiResponse(responseCode = "422", description = "Subconta não elegível para depósitos (status inválido)")
    })
    public ResponseEntity<DepositResponse> createPixDeposit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for deposits");
        }
        request.setIdempotencyKey(idempotencyKey.trim());
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), request.getSubaccountId(), PermissionCodes.TRANSACTIONS_CREATE);
        DepositResponse response = depositService.createPixDeposit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar depósitos",
            description = "Lista depósitos paginados. Forneça `walletId` ou `subaccountId` como filtro obrigatório. "
                    + "Exemplo: `GET /api/v1/deposits?subaccountId=xxx&page=0&size=10&sort=createdAt,desc`")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "walletId ou subaccountId obrigatório")
    })
    public ResponseEntity<Page<DepositResponse>> findAll(
            @RequestParam(required = false) UUID walletId,
            @RequestParam(required = false) UUID subaccountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID actor = actorResolver.requireProductUserId();
        if (walletId != null) {
            resourceAuthorization.requireWallet(actor, walletId, PermissionCodes.TRANSACTIONS_READ);
        } else if (subaccountId != null) {
            resourceAuthorization.requireSubaccount(actor, subaccountId, PermissionCodes.TRANSACTIONS_READ);
        } else {
            throw new InvalidRequestException("walletId or subaccountId is required");
        }
        return ResponseEntity.ok(depositService.findAll(walletId, subaccountId, pageable));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get deposit by transaction ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<DepositResponse> findById(@PathVariable UUID transactionId) {
        resourceAuthorization.requireTransaction(
                actorResolver.requireProductUserId(), transactionId, PermissionCodes.TRANSACTIONS_READ);
        return ResponseEntity.ok(depositService.findById(transactionId));
    }

    @GetMapping("/{transactionId}/pix-qr-code")
    @Operation(summary = "Get PIX QR code for a deposit",
            description = "Returns the base64-encoded QR code image and copia-e-cola payload for a pending deposit")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR code retrieved"),
            @ApiResponse(responseCode = "404", description = "Transaction not found or QR code not available")
    })
    public ResponseEntity<PixQrCodeResponse> getPixQrCode(@PathVariable UUID transactionId) {
        resourceAuthorization.requireTransaction(
                actorResolver.requireProductUserId(), transactionId, PermissionCodes.TRANSACTIONS_READ);
        return ResponseEntity.ok(depositService.getPixQrCode(transactionId));
    }
}
