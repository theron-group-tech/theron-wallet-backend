package com.theron.wallet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Successful login response with JWT token")
public class LoginResponse {

    @Schema(description = "Bearer JWT token — include in Authorization header for protected routes")
    private String token;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Expiration in milliseconds from now")
    private long expiresIn;

    @Schema(description = "Admin user ID")
    private UUID adminId;

    @Schema(description = "Admin name")
    private String name;

    @Schema(description = "Admin email")
    private String email;

    @Schema(description = "Admin role", example = "ADMIN")
    private String role;
}

