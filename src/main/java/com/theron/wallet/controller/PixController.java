package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateAccountPixQrCodeRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.response.AccountPixKeyResponse;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.PixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pix")
@RequiredArgsConstructor
@Tag(name = "PIX", description = "Theron PIX API. Requires JWT access token. Never exposes Asaas credentials.")
public class PixController {
    private final PixService pixService;
    private final ActorResolver actorResolver;

    @PostMapping("/keys")
    @Operation(summary = "Create PIX key for an Account", description = "Asaas API only creates EVP (random) keys.")
    public ResponseEntity<AccountPixKeyResponse> createKey(
            @Valid @RequestBody CreateAccountPixKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pixService.createKey(actorResolver.requireProductUserId(), request));
    }

    @GetMapping("/keys")
    @Operation(summary = "List PIX keys of an Account")
    public ResponseEntity<List<AccountPixKeyResponse>> listKeys(
            @RequestParam UUID accountId) {
        return ResponseEntity.ok(pixService.listKeys(actorResolver.requireProductUserId(), accountId));
    }

    @DeleteMapping("/keys/{id}")
    @Operation(summary = "Remove PIX key (logical + Asaas)")
    public ResponseEntity<Void> deleteKey(
            @PathVariable UUID id
            ) {
        pixService.deleteKey(actorResolver.requireProductUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transfers")
    @Operation(summary = "Create PIX transfer", description = "Idempotency-Key header is mandatory.")
    public ResponseEntity<PixTransferResponse> createTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePixTransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key is required for PIX transfers");
        }
        request.setIdempotencyKey(idempotencyKey.trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pixService.createTransfer(actorResolver.requireProductUserId(), request));
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Get PIX transfer by id")
    public ResponseEntity<PixTransferResponse> getTransfer(
            @PathVariable UUID id
            ) {
        return ResponseEntity.ok(pixService.getTransfer(actorResolver.requireProductUserId(), id));
    }

    @GetMapping("/transfers")
    @Operation(summary = "List PIX transfers of an Account")
    public ResponseEntity<Page<PixTransferResponse>> listTransfers(
            @RequestParam UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pixService.listTransfers(
                actorResolver.requireProductUserId(), accountId, pageable));
    }

    @PostMapping("/qr-codes")
    @Operation(summary = "Create static PIX QR code")
    public ResponseEntity<AccountPixQrCodeResponse> createQrCode(
            @Valid @RequestBody CreateAccountPixQrCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pixService.createQrCode(actorResolver.requireProductUserId(), request));
    }
}
