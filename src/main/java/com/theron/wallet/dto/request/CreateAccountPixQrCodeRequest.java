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
@Schema(description = "Create a static PIX QR code for an Account PIX key")
public class CreateAccountPixQrCodeRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    private UUID pixKeyId;

    @DecimalMin(value = "0.01", message = "Value must be at least R$ 0.01")
    private BigDecimal value;

    @Size(max = 140)
    private String description;
}
