package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AsaasBindStatus;
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
public class AsaasBindResponse {

    private UUID accountId;
    private UUID organizationId;
    private String asaasAccountId;
    private String asaasWalletId;
    private AsaasBindStatus status;
    private String message;
    private String asaasCommercialStatus;
    private String asaasDocumentationStatus;
    private String asaasGeneralStatus;
    private String onboardingUrl;
}
