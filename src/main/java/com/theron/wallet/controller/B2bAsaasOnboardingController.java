package com.theron.wallet.controller;

import com.theron.wallet.dto.request.onboarding.B2bAsaasOnboardingSubmitRequest;
import com.theron.wallet.dto.response.AsaasOnboardingResponse;
import com.theron.wallet.dto.response.AsaasSubaccountStatusResponse;
import com.theron.wallet.security.Actor;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.AsaasOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * B2B (OAuth client_credentials) Asaas onboarding — Account from credentials, no product JWT.
 */
@RestController
@RequestMapping("/api/v1/asaas")
@RequiredArgsConstructor
@Tag(name = "B2B Asaas Onboarding", description = "Partner self-service Asaas subaccount provisioning")
public class B2bAsaasOnboardingController {

    private final AsaasOnboardingService asaasOnboardingService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @GetMapping("/onboarding/b2b")
    @Operation(summary = "Get Asaas onboarding state for the OAuth-bound Account")
    public ResponseEntity<AsaasOnboardingResponse> get() {
        UUID accountId = requireBoundAccount(PermissionCodes.ONBOARDING_READ);
        return ResponseEntity.ok(asaasOnboardingService.getCurrentForAccount(accountId));
    }

    @PostMapping("/onboarding/b2b")
    @Operation(summary = "One-shot KYC + create Asaas subaccount for the OAuth-bound Account")
    public ResponseEntity<AsaasOnboardingResponse> submit(
            @Valid @RequestBody B2bAsaasOnboardingSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID accountId = requireBoundAccount(PermissionCodes.ONBOARDING_SUBMIT);
        return ResponseEntity.ok(
                asaasOnboardingService.submitOneShotForAccount(accountId, request, idempotencyKey));
    }

    @GetMapping("/subaccount/status/b2b")
    @Operation(summary = "Get compact subaccount/onboarding status for the OAuth-bound Account")
    public ResponseEntity<AsaasSubaccountStatusResponse> status() {
        UUID accountId = requireBoundAccount(PermissionCodes.ONBOARDING_READ);
        return ResponseEntity.ok(asaasOnboardingService.subaccountStatusForAccount(accountId));
    }

    private UUID requireBoundAccount(String scope) {
        Actor actor = actorResolver.requireActor();
        return resourceAuthorization.requireBoundAccount(actor, scope);
    }
}
