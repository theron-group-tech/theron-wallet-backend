package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update max amount and/or enabled flag of a transaction limit")
public class UpdateTransactionLimitRequest {

    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal maxAmount;

    private Boolean enabled;
}
