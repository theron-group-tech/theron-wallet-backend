package com.theron.wallet.dto.request;

import com.theron.wallet.enums.LimitPeriod;
import com.theron.wallet.enums.LimitTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a hierarchical transaction limit. Omit account/user/role for org-wide.")
public class CreateTransactionLimitRequest {

    @NotNull
    private UUID organizationId;

    private UUID accountId;

    private UUID userId;

    private UUID roleId;

    @NotNull
    private LimitTransactionType transactionType;

    @NotNull
    private LimitPeriod period;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal maxAmount;
}
