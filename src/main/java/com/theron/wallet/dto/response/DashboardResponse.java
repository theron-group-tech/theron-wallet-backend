package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    private BigDecimal balance;
    /** Local ledger total for the same scope (for divergence / audit). */
    private BigDecimal ledgerBalance;
    private BigDecimal availableBalance;
    private BigDecimal blockedBalance;
    private String currency;
    private BigDecimal todayIncome;
    private BigDecimal todayExpenses;
    private List<TransactionResponse> pendingTransactions;
    private List<TransactionResponse> recentTransactions;
    private UUID organizationId;
    private UUID accountId;
}
