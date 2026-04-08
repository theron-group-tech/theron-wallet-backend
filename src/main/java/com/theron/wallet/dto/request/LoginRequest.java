package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin login credentials")
public class LoginRequest {

    @NotBlank
    @Email
    @Schema(description = "Admin email", example = "admin@therongroup.com", required = true)
    private String email;

    @NotBlank
    @Schema(description = "Admin password", example = "admin123", required = true)
    private String password;
}

