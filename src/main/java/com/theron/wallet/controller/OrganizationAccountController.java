package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/accounts")
@RequiredArgsConstructor
@Tag(name = "Organization Accounts", description = "Financial accounts owned by an organization. organizationId comes from the path only.")
public class OrganizationAccountController {

    private final AccountService accountService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @PostMapping
    @Operation(summary = "Create account", description = "Creates an account, a zero-balance wallet, and provisions an Asaas subaccount when the caller is the account owner.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "422", description = "Organization is not ACTIVE")
    })
    public ResponseEntity<AccountResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateAccountRequest request) {
        UUID actor = actorResolver.requireProductUserId();
        resourceAuthorization.requireOrganization(actor, organizationId, PermissionCodes.ORGANIZATION_UPDATE);
        AccountResponse created = accountService.create(organizationId, request, actor);
        resourceAuthorization.requirePathOrganization(organizationId, created.getOrganizationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List accounts of the organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found")
    })
    public ResponseEntity<List<AccountResponse>> list(@PathVariable UUID organizationId) {
        UUID actor = actorResolver.requireProductUserId();
        resourceAuthorization.requireOrganization(actor, organizationId, PermissionCodes.WALLET_READ);
        List<AccountResponse> accounts = accountService.listByOrganization(organizationId);
        if (!resourceAuthorization.isOrgWideViewer(actor, organizationId)) {
            accounts = accounts.stream()
                    .filter(account -> actor.equals(account.getOwnerUserId()))
                    .toList();
        }
        return ResponseEntity.ok(accounts);
    }
}
