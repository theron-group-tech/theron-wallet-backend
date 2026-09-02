package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Create a static PIX QR code for a Platform Account (Master) PIX key")
public class CreatePlatformPixQrCodeRequest {

    @NotBlank
    @Schema(description = "Asaas PIX key id from GET /admin/platform-account/pix/keys")
    private String pixKeyId;

    @DecimalMin(value = "0.01", message = "Value must be at least R$ 0.01")
    private BigDecimal value;

    @Size(max = 140)
    private String description;
}
