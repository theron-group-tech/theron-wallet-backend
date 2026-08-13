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
@Schema(description = "Create a product user (person)")
public class CreateUserRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(example = "Maria Silva")
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(example = "maria@empresa.com.br")
    private String email;

    @Size(max = 20)
    @Schema(example = "11999998888")
    private String phone;

    @NotBlank
    @Size(min = 8, max = 100)
    @Schema(description = "Plain password — stored as BCrypt hash", example = "SenhaForte1!")
    private String password;
}
