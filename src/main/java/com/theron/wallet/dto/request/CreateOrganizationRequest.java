package com.theron.wallet.dto.request;

import com.theron.wallet.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request body to create a Theron organization (company tenant)")
public class CreateOrganizationRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Legal / registered company name", example = "Acme Tecnologia LTDA")
    private String legalName;

    @Size(max = 255)
    @Schema(description = "Trade name (optional)", example = "Acme")
    private String tradeName;

    @NotBlank
    @Schema(description = "CNPJ (14 digits). Non-digits are stripped before persistence.",
            example = "12345678000199")
    private String document;

    @NotNull
    @Schema(description = "Document type — must be CNPJ for Asaas BaaS", example = "CNPJ", allowableValues = {"CNPJ"})
    private DocumentType documentType;
}
