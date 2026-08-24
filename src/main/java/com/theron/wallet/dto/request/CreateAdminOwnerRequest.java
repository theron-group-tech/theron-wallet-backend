package com.theron.wallet.dto.request;

import com.theron.wallet.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Platform admin creates the organization OWNER (user + membership + account + Asaas)")
public class CreateAdminOwnerRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @Size(max = 20)
    private String phone;

    @NotBlank
    @Schema(description = "OWNER CNPJ (14 digits) for Asaas subaccount")
    private String document;

    @NotNull
    @Builder.Default
    private DocumentType documentType = DocumentType.CNPJ;
}
