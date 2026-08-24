package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreatePixKeyRequest;
import com.theron.wallet.dto.request.CreatePixStaticQrCodeRequest;
import com.theron.wallet.dto.response.PixKeyResponse;
import com.theron.wallet.dto.response.PixStaticQrCodeResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.PixKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subaccounts/{subaccountId}/pix")
@RequiredArgsConstructor
@Tag(name = "PIX Keys", description = "Gerenciamento de chaves Pix e QR Codes estáticos das subcontas")
public class PixKeyController {

    private final PixKeyService pixKeyService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    // ── Chaves Pix ───────────────────────────────────────────────────────────

    @PostMapping("/keys")
    @Operation(
            summary = "Criar chave Pix",
            description = "Registra uma nova chave Pix aleatória (EVP) para a subconta. A API Asaas não cria CPF, CNPJ, EMAIL ou PHONE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Chave Pix criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Tipo de chave inválido ou ausente"),
            @ApiResponse(responseCode = "403", description = "Subconta não elegível (status inválido)"),
            @ApiResponse(responseCode = "404", description = "Subconta não encontrada"),
            @ApiResponse(responseCode = "422", description = "Erro de validação retornado pelo Asaas")
    })
    public ResponseEntity<PixKeyResponse> createPixKey(
            @PathVariable UUID subaccountId,
            @Valid @RequestBody CreatePixKeyRequest request) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.PIX_CREATE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pixKeyService.createPixKey(subaccountId, request));
    }

    @GetMapping("/keys")
    @Operation(
            summary = "Listar chaves Pix",
            description = "Retorna todas as chaves Pix registradas para a subconta no Asaas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de chaves Pix"),
            @ApiResponse(responseCode = "403", description = "Subconta não elegível (status inválido)"),
            @ApiResponse(responseCode = "404", description = "Subconta não encontrada")
    })
    public ResponseEntity<List<PixKeyResponse>> listPixKeys(@PathVariable UUID subaccountId) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.PIX_READ);
        return ResponseEntity.ok(pixKeyService.listPixKeys(subaccountId));
    }

    @DeleteMapping("/keys/{pixKeyId}")
    @Operation(
            summary = "Excluir chave Pix",
            description = "Remove uma chave Pix da subconta."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Chave Pix removida com sucesso"),
            @ApiResponse(responseCode = "403", description = "Subconta não elegível (status inválido)"),
            @ApiResponse(responseCode = "404", description = "Subconta ou chave não encontrada")
    })
    public ResponseEntity<Void> deletePixKey(
            @PathVariable UUID subaccountId,
            @PathVariable String pixKeyId) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.PIX_CREATE);
        pixKeyService.deletePixKey(subaccountId, pixKeyId);
        return ResponseEntity.noContent().build();
    }

    // ── QR Codes Estáticos ───────────────────────────────────────────────────

    @PostMapping("/keys/{pixKeyId}/qrcodes/static")
    @Operation(
            summary = "Criar QR Code Pix estático",
            description = """
                    Gera um QR Code Pix estático vinculado à chave informada.
                    - `value` **opcional** — se omitido, cria QR Code aberto (pagador define o valor).
                    - `value` **informado** — cria QR Code com valor fixo.
                    - O payload e a imagem base64 são retornados diretamente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "QR Code estático criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "403", description = "Subconta não elegível (status inválido)"),
            @ApiResponse(responseCode = "404", description = "Subconta ou chave Pix não encontrada")
    })
    public ResponseEntity<PixStaticQrCodeResponse> createStaticQrCode(
            @PathVariable UUID subaccountId,
            @PathVariable String pixKeyId,
            @Valid @RequestBody CreatePixStaticQrCodeRequest request) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.PIX_CREATE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pixKeyService.createStaticQrCode(subaccountId, pixKeyId, request));
    }

    @DeleteMapping("/qrcodes/static/{qrCodeId}")
    @Operation(
            summary = "Excluir QR Code Pix estático",
            description = "Remove um QR Code Pix estático."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "QR Code removido com sucesso"),
            @ApiResponse(responseCode = "403", description = "Subconta não elegível (status inválido)"),
            @ApiResponse(responseCode = "404", description = "Subconta ou QR Code não encontrado")
    })
    public ResponseEntity<Void> deleteStaticQrCode(
            @PathVariable UUID subaccountId,
            @PathVariable String qrCodeId) {
        resourceAuthorization.requireSubaccount(
                actorResolver.requireProductUserId(), subaccountId, PermissionCodes.PIX_CREATE);
        pixKeyService.deleteStaticQrCode(subaccountId, qrCodeId);
        return ResponseEntity.noContent().build();
    }
}
