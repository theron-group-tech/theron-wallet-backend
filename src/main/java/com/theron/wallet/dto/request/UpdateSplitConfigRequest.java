package com.theron.wallet.dto.request;

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
public class UpdateSplitConfigRequest {

    @DecimalMin("0")
    private BigDecimal percent;

    @DecimalMin("0")
    private BigDecimal fixedAmount;

    private Boolean enabled;
}
