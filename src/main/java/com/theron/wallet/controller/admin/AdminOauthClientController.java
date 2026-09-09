package com.theron.wallet.controller.admin;

import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.request.UpdateOauthClientAccountsRequest;
import com.theron.wallet.dto.request.UpdateOauthClientScopesRequest;
import com.theron.wallet.dto.response.OauthClientResponse;
import com.theron.wallet.dto.response.OauthClientSecretResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.UserPrincipal;
import com.theron.wallet.service.OauthClientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/oauth-clients")
@RequiredArgsConstructor
@Tag(name = "Platform Admin OAuth Clients", description = "Manage B2B OAuth clients per organization")
public class AdminOauthClientController {

    private final ActorResolver actorResolver;
    private final OauthClientAdminService oauthClientAdminService;

    @PostMapping
    @Operation(summary = "Create OAuth client (returns plaintext secret once)")
    public ResponseEntity<OauthClientSecretResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateOauthClientRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(oauthClientAdminService.create(organizationId, request, admin.getAdminId()));
    }

    @GetMapping
    @Operation(summary = "List OAuth clients for an organization")
    public ResponseEntity<List<OauthClientResponse>> list(@PathVariable UUID organizationId) {
        actorResolver.requireAdmin();
        return ResponseEntity.ok(oauthClientAdminService.list(organizationId));
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "Rotate client secret (returns plaintext secret once)")
    public ResponseEntity<OauthClientSecretResponse> rotateSecret(
            @PathVariable UUID organizationId,
            @PathVariable UUID id) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(oauthClientAdminService.rotateSecret(organizationId, id, admin.getAdminId()));
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke an OAuth client")
    public ResponseEntity<OauthClientResponse> revoke(
            @PathVariable UUID organizationId,
            @PathVariable UUID id) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(oauthClientAdminService.revoke(organizationId, id, admin.getAdminId()));
    }

    @PutMapping("/{id}/scopes")
    @Operation(summary = "Replace OAuth client scopes")
    public ResponseEntity<OauthClientResponse> replaceScopes(
            @PathVariable UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOauthClientScopesRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(
                oauthClientAdminService.replaceScopes(organizationId, id, request, admin.getAdminId()));
    }

    @PutMapping("/{id}/accounts")
    @Operation(summary = "Replace OAuth client account bindings")
    public ResponseEntity<OauthClientResponse> replaceAccounts(
            @PathVariable UUID organizationId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOauthClientAccountsRequest request) {
        UserPrincipal admin = actorResolver.requireAdmin();
        return ResponseEntity.ok(
                oauthClientAdminService.replaceAccounts(organizationId, id, request, admin.getAdminId()));
    }
}
