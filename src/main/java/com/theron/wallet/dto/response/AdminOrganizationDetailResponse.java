package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AsaasBindStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminOrganizationDetailResponse {

    private OrganizationResponse organization;
    private List<AdminAccountSummary> accounts;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdminAccountSummary {
        private UUID accountId;
        private UUID ownerUserId;
        private String name;
        private String type;
        private String status;
        private AsaasBindStatus asaasStatus;
        private String asaasAccountId;
        private String asaasWalletId;
        private LocalDateTime createdAt;
        /** Local ledger ({@code wallet.balance}). */
        private BigDecimal walletBalance;
        /** Asaas {@code /finance/balance} when subaccount ACTIVE. */
        private BigDecimal asaasBalance;
        private Boolean asaasBalanceUnavailable;
    }
}
