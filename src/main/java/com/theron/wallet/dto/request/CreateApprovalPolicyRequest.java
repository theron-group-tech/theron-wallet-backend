package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
@Schema(description = "Create an approval policy band for an Account")
public class CreateApprovalPolicyRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal amountMin;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal amountMax;

    @NotNull
    @Min(0)
    private Integer requiredApprovals;
}
