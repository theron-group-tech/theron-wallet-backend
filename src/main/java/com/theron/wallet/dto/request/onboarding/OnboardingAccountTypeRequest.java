package com.theron.wallet.dto.request.onboarding;

import com.theron.wallet.enums.AsaasPersonType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingAccountTypeRequest {

    @NotNull
    private AsaasPersonType personType;
}
