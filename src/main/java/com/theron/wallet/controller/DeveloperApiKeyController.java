package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateTheronApiKeyRequest;
import com.theron.wallet.dto.response.TheronApiKeyResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.DeveloperApiKeyService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/developer/api-keys")
@RequiredArgsConstructor
@Tag(name = "Developer API Keys", description = "Theron API Keys for external systems (OWNER only). Not Asaas credentials.")
public class DeveloperApiKeyController {

    private final DeveloperApiKeyService developerApiKeyService;
    private final ActorResolver actorResolver;

    @PostMapping
    @Operation(summary = "Create Theron API Key (plaintext returned once)")
    public ResponseEntity<TheronApiKeyResponse> create(@Valid @RequestBody CreateTheronApiKeyRequest request) {
        UUID userId = actorResolver.requireProductUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(developerApiKeyService.create(userId, request));
    }

    @GetMapping
    @Operation(summary = "List API Keys (never returns plaintext secret)")
    public ResponseEntity<List<TheronApiKeyResponse>> list() {
        UUID userId = actorResolver.requireProductUserId();
        return ResponseEntity.ok(developerApiKeyService.list(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API Key")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        UUID userId = actorResolver.requireProductUserId();
        developerApiKeyService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate")
    @Operation(summary = "Rotate API Key (new plaintext returned once; previous invalidated)")
    public ResponseEntity<TheronApiKeyResponse> rotate(@PathVariable UUID id) {
        UUID userId = actorResolver.requireProductUserId();
        return ResponseEntity.ok(developerApiKeyService.rotate(userId, id));
    }
}
