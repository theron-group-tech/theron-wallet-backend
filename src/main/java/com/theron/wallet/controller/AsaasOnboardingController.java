package com.theron.wallet.controller;

import com.theron.wallet.dto.request.onboarding.OnboardingAccountTypeRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingBusinessRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.AsaasOnboardingResponse;
import com.theron.wallet.dto.response.AsaasSubaccountStatusResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.AsaasOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asaas")
@RequiredArgsConstructor
@Tag(name = "Asaas Onboarding", description = "Self-service financial onboarding for the authenticated user")
public class AsaasOnboardingController {

    private final AsaasOnboardingService asaasOnboardingService;
    private final ActorResolver actorResolver;

    @PostMapping("/onboarding")
    @Operation(summary = "Start or resume financial onboarding")
    public ResponseEntity<AsaasOnboardingResponse> start() {
        return ResponseEntity.ok(asaasOnboardingService.startOrResume(actorResolver.requireProductUserId()));
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Get current onboarding state")
    public ResponseEntity<AsaasOnboardingResponse> get() {
        return ResponseEntity.ok(asaasOnboardingService.getCurrent(actorResolver.requireProductUserId()));
    }

    @PutMapping("/onboarding/personal")
    @Operation(summary = "Save personal data (CPF)")
    public ResponseEntity<AsaasOnboardingResponse> personal(
            @Valid @RequestBody OnboardingPersonalRequest request) {
        return ResponseEntity.ok(asaasOnboardingService.savePersonal(actorResolver.requireProductUserId(), request));
    }

    @PutMapping("/onboarding/business")
    @Operation(summary = "Save business data (CNPJ)")
    public ResponseEntity<AsaasOnboardingResponse> business(
            @Valid @RequestBody OnboardingBusinessRequest request) {
        return ResponseEntity.ok(asaasOnboardingService.saveBusiness(actorResolver.requireProductUserId(), request));
    }

    @PutMapping("/onboarding/address")
    @Operation(summary = "Save address")
    public ResponseEntity<AsaasOnboardingResponse> address(
            @Valid @RequestBody OnboardingAddressRequest request) {
        return ResponseEntity.ok(asaasOnboardingService.saveAddress(actorResolver.requireProductUserId(), request));
    }

    @PutMapping("/onboarding/financial")
    @Operation(summary = "Save financial data")
    public ResponseEntity<AsaasOnboardingResponse> financial(
            @Valid @RequestBody OnboardingFinancialRequest request) {
        return ResponseEntity.ok(asaasOnboardingService.saveFinancial(actorResolver.requireProductUserId(), request));
    }

    @PostMapping("/onboarding/submit")
    @Operation(summary = "Submit onboarding and create Asaas subaccount")
    public ResponseEntity<AsaasOnboardingResponse> submit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(asaasOnboardingService.submit(actorResolver.requireProductUserId(), idempotencyKey));
    }

    @GetMapping("/subaccount/status")
    @Operation(summary = "Get subaccount and onboarding status for the authenticated user")
    public ResponseEntity<AsaasSubaccountStatusResponse> status() {
        return ResponseEntity.ok(asaasOnboardingService.subaccountStatus(actorResolver.requireProductUserId()));
    }

    @PutMapping("/onboarding/account-type")
    @Operation(summary = "Choose account type CPF or CNPJ")
    public ResponseEntity<AsaasOnboardingResponse> accountType(
            @Valid @RequestBody OnboardingAccountTypeRequest request) {
        return ResponseEntity.ok(asaasOnboardingService.saveAccountType(actorResolver.requireProductUserId(), request));
    }
}
