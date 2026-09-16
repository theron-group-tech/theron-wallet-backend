package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.AccountType;
import com.theron.wallet.enums.AsaasBindStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {

    private UUID id;
    private UUID organizationId;
    private UUID ownerUserId;
    private String name;
    private AccountType type;
    private AccountStatus status;
    private String currency;
    private AsaasBindStatus asaasStatus;
    private String asaasAccountId;
    private String asaasWalletId;
    private String asaasMessage;
    private String asaasCommercialStatus;
    private String asaasDocumentationStatus;
    private String asaasGeneralStatus;
    private String onboardingUrl;
    private com.theron.wallet.enums.AsaasOnboardingStatus onboardingStatus;
    private Boolean financialResourcesEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
