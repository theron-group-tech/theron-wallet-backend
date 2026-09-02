package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.domain.onboarding.AsaasOnboardingPayload;
import com.theron.wallet.enums.AsaasOnboardingStatus;
import com.theron.wallet.enums.AsaasOnboardingStep;
import com.theron.wallet.enums.AsaasPersonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasOnboardingResponse {

    private UUID id;
    private UUID accountId;
    private AsaasPersonType personType;
    private AsaasOnboardingStep currentStep;
    private AsaasOnboardingStatus status;
    private AsaasOnboardingPayload review;
    private String onboardingUrl;
    private String asaasAccountId;
    private String lastErrorCode;
    private String lastErrorMessage;
    private boolean financialResourcesEnabled;
}
