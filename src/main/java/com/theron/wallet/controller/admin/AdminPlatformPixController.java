package com.theron.wallet.controller.admin;

import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.PlatformPixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/platform-account/pix")
@RequiredArgsConstructor
@Tag(name = "Admin Platform PIX", description = "PIX keys on the Asaas Master (Platform Account). Uses ASAAS_API_KEY.")
public class AdminPlatformPixController {

    private final ActorResolver actorResolver;
    private final PlatformPixService platformPixService;

    @GetMapping("/keys")
    @Operation(summary = "List Platform Account PIX keys (Master)")
    public ResponseEntity<List<PlatformPixKeyResponse>> listKeys() {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(platformPixService.listKeys());
    }

    @PostMapping("/keys")
    @Operation(summary = "Create Platform Account PIX key", description = "Only EVP (random) keys via Asaas Master API key.")
    public ResponseEntity<PlatformPixKeyResponse> createKey(
            @Valid @RequestBody CreatePlatformPixKeyRequest request) {
        actorResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(platformPixService.createKey(request));
    }

    @DeleteMapping("/keys/{id}")
    @Operation(summary = "Delete Platform Account PIX key by Asaas id")
    public ResponseEntity<Void> deleteKey(@PathVariable String id) {
        actorResolver.requireAdmin();
        platformPixService.deleteKey(id);
        return ResponseEntity.noContent().build();
    }
}
