package com.theron.wallet.dto.request;

import com.theron.wallet.enums.PixKeyType;
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
@Schema(description = "PIX transfer from the Platform Account (Asaas Master). Idempotency-Key header is mandatory.")
public class CreatePlatformPixTransferRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be at least R$ 0.01")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 100)
    private String destinationPixKey;

    @NotNull
    @Schema(allowableValues = {"CPF", "CNPJ", "EMAIL", "PHONE", "EVP"})
    private PixKeyType destinationPixKeyType;

    @Size(max = 255)
    private String description;
}
