package com.theron.wallet.dto.request;

import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.RoleCode;
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
@Schema(description = "Create an employee in the authenticated organization (user + membership + account + Asaas)")
public class CreateOrganizationEmployeeRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 20)
    private String phone;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Schema(description = "Employee CNPJ (14 digits, MEI/filial). Required for Asaas subaccount.")
    private String document;

    @NotNull
    @Builder.Default
    private DocumentType documentType = DocumentType.CNPJ;

    @NotNull
    @Schema(description = "Product role: FINANCE or EMPLOYEE", example = "EMPLOYEE")
    @Builder.Default
    private RoleCode role = RoleCode.EMPLOYEE;
}
