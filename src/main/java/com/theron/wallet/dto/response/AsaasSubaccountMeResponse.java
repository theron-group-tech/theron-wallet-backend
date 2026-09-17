package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AsaasOnboardingStatus;
import com.theron.wallet.enums.SubaccountStatus;
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
public class AsaasSubaccountMeResponse {

    private UUID accountId;
    private String asaasAccountId;
    private String walletId;
    private SubaccountStatus status;
    private AsaasOnboardingStatus onboardingStatus;
    private boolean approved;
    private boolean hasSubaccount;
    private String onboardingUrl;
    private String message;
}
