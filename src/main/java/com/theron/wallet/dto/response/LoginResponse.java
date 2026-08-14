package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Successful login. Product users receive access+refresh; admins receive the dashboard JWT.")
public class LoginResponse {

    @Schema(description = "Access JWT — alias of accessToken for existing clients")
    private String token;

    @Schema(description = "Product access JWT (15 min)")
    private String accessToken;

    @Schema(description = "Opaque refresh token — returned once, never logged")
    private String refreshToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token expiration in milliseconds from now")
    private long expiresIn;

    @Schema(description = "Product user ID")
    private UUID userId;

    @Schema(description = "Admin user ID (dashboard login only)")
    private UUID adminId;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Account email")
    private String email;

    @Schema(description = "Admin role (dashboard login only)", example = "ADMIN")
    private String role;
}
