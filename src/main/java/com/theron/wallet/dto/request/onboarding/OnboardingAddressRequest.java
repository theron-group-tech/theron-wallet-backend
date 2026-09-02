package com.theron.wallet.dto.request.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingAddressRequest {

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 20)
    private String addressNumber;

    @Size(max = 100)
    private String complement;

    @NotBlank
    @Size(max = 100)
    private String province;

    @NotBlank
    @Size(max = 10)
    private String postalCode;

    @Size(max = 100)
    private String city;
}
