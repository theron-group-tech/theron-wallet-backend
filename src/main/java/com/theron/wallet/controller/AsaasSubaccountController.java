package com.theron.wallet.controller;

import com.theron.wallet.dto.request.onboarding.B2bAsaasOnboardingSubmitRequest;
import com.theron.wallet.dto.response.AsaasOnboardingResponse;
import com.theron.wallet.dto.response.AsaasSubaccountMeResponse;
import com.theron.wallet.dto.response.AsaasSubaccountStatusResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.OrganizationContextResolver;
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
 * Product JWT aliases for Asaas subaccount lifecycle (OWNER).
 * Tenant resolved from authenticated user — never from body accountId.
 */
@RestController
@RequestMapping("/api/v1/asaas/subaccounts")
@RequiredArgsConstructor
@Tag(name = "Asaas Subaccounts", description = "Create/status Asaas subaccount for the authenticated OWNER account")
public class AsaasSubaccountController {

    private final AsaasOnboardingService asaasOnboardingService;
    private final ActorResolver actorResolver;
    private final OrganizationContextResolver organizationContextResolver;
    private final AccountRepository accountRepository;
    private final SubaccountRepository subaccountRepository;

    @PostMapping
    @Operation(summary = "Create Asaas subaccount (OWNER one-shot KYC)")
    public ResponseEntity<AsaasOnboardingResponse> create(
            @Valid @RequestBody B2bAsaasOnboardingSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID userId = actorResolver.requireProductUserId();
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(userId);
        organizationContextResolver.requireOwner(organizationId, userId);
        Account account = accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, userId)
                .orElseThrow(() -> new ForbiddenException("Owner account not found"));
        return ResponseEntity.ok(
                asaasOnboardingService.submitOneShotForAccount(account.getId(), request, idempotencyKey));
    }

    @GetMapping("/me")
    @Operation(summary = "Get own Asaas subaccount status")
    public ResponseEntity<AsaasSubaccountMeResponse> me() {
        UUID userId = actorResolver.requireProductUserId();
        AsaasSubaccountStatusResponse status = asaasOnboardingService.subaccountStatus(userId);
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(userId);
        Account account = accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "ownerUserId", userId));
        Subaccount subaccount = subaccountRepository.findByAccount_Id(account.getId()).orElse(null);

        return ResponseEntity.ok(AsaasSubaccountMeResponse.builder()
                .accountId(status.getAccountId())
                .asaasAccountId(subaccount != null ? subaccount.getAsaasAccountId() : null)
                .walletId(subaccount != null ? subaccount.getAsaasWalletId() : null)
                .status(status.getSubaccountStatus())
                .onboardingStatus(status.getOnboardingStatus())
                .approved(status.isFinancialResourcesEnabled())
                .hasSubaccount(status.isHasSubaccount())
                .onboardingUrl(status.getOnboardingUrl())
                .message(status.getMessage())
                .build());
    }
}
