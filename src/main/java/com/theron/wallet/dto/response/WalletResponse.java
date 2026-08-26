package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletResponse {

    private UUID id;
    private UUID subaccountId;
    private UUID accountId;
    private BigDecimal balance;
    /** Local ledger mirror ({@code wallet.balance}). */
    private BigDecimal ledgerBalance;
    private String currency;
    private Boolean active;
    /** True when Asaas ACTIVE subaccount exists but balance could not be fetched. */
    private Boolean asaasBalanceUnavailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
