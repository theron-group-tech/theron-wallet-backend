package com.theron.wallet.dto.request.onboarding;

import com.theron.wallet.enums.AsaasPersonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One-shot Asaas onboarding payload for B2B OAuth clients.
 * Provide {@code personal} for INDIVIDUAL or {@code business} for COMPANY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class B2bAsaasOnboardingSubmitRequest {

    @NotNull
    private AsaasPersonType personType;

    @Valid
    private OnboardingPersonalRequest personal;

    @Valid
    private OnboardingBusinessRequest business;

    @NotNull
    @Valid
    private OnboardingAddressRequest address;

    @NotNull
    @Valid
    private OnboardingFinancialRequest financial;
}
