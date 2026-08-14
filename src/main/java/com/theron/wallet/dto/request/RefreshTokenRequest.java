package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refresh token presented by the client")
public class RefreshTokenRequest {

    @NotBlank
    @Schema(description = "Opaque refresh token issued at login", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
