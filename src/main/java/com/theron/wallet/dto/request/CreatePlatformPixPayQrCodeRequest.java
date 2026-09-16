package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pay a PIX QR code (copia e cola) from the Platform Account (Asaas Master).")
public class CreatePlatformPixPayQrCodeRequest {

    @NotBlank
    @Schema(description = "EMV copia e cola payload (starts with 000201...)")
    private String payload;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be at least R$ 0.01")
    private BigDecimal amount;

    @Size(max = 255)
    private String description;
}
