package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Create a payment order (FINANCE/OWNER). Source is always the OWNER account.")
public class CreatePaymentOrderRequest {

    @NotNull
    private UUID organizationId;

    @NotNull
    private UUID destinationAccountId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @Size(max = 3)
    @Schema(description = "Currency code", example = "BRL", defaultValue = "BRL")
    @Builder.Default
    private String currency = "BRL";

    @Size(max = 500)
    private String description;
}
