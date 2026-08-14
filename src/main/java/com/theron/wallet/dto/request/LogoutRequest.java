package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional refresh token when logging out without a Bearer access token")
public class LogoutRequest {

    @Schema(description = "Opaque refresh token (optional if Authorization Bearer is present)")
    private String refreshToken;
}
