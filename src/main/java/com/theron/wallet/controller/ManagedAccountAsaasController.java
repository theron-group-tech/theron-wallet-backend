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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization/accounts/{accountId}/asaas")
@RequiredArgsConstructor
@Tag(name = "Managed Account Asaas", description = "OWNER/API-key onboarding for managed Finance/Employee accounts")
public class ManagedAccountAsaasController {

    private final AsaasOnboardingService onboardingService;
    private final ActorResolver actorResolver;
    private final ResourceAuthorization resourceAuthorization;

    @PostMapping("/onboarding")
    @Operation(summary = "Create or resume Asaas subaccount for a managed account")
    public ResponseEntity<AsaasOnboardingResponse> submit(
            @PathVariable UUID accountId,
            @Valid @RequestBody B2bAsaasOnboardingSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Actor actor = actorResolver.requireActor();
        resourceAuthorization.requireAccount(actor, accountId, PermissionCodes.ONBOARDING_SUBMIT);
        return ResponseEntity.ok(
                onboardingService.submitOneShotForAccount(accountId, request, idempotencyKey));
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Get current Asaas onboarding state for a managed account")
    public ResponseEntity<AsaasOnboardingResponse> getOnboarding(@PathVariable UUID accountId) {
        Actor actor = actorResolver.requireActor();
        resourceAuthorization.requireAccount(actor, accountId, PermissionCodes.ONBOARDING_READ);
        return ResponseEntity.ok(onboardingService.getCurrentForAccount(accountId));
    }

    @GetMapping("/subaccount/status")
    @Operation(summary = "Get Asaas subaccount status for a managed account")
    public ResponseEntity<AsaasSubaccountStatusResponse> status(@PathVariable UUID accountId) {
        Actor actor = actorResolver.requireActor();
        resourceAuthorization.requireAccount(actor, accountId, PermissionCodes.ONBOARDING_READ);
        return ResponseEntity.ok(onboardingService.subaccountStatusForAccount(accountId));
    }
}
