package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login credentials. Optional device fields are used for product (mobile) sessions.")
public class LoginRequest {

    @NotBlank
    @Email
    @Schema(description = "Account email", example = "maria@empresa.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank
    @Schema(description = "Account password", example = "SenhaForte1!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 128)
    @Schema(description = "Stable client device identifier (optional, product login)")
    private String deviceId;

    @Size(max = 255)
    @Schema(description = "Human-readable device name (optional)")
    private String deviceName;

    @Size(max = 64)
    @Schema(description = "Client platform, e.g. ios, android, web (optional)")
    private String platform;
}
