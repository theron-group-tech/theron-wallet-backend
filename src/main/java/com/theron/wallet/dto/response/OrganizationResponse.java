package com.theron.wallet.dto.response;

import com.theron.wallet.enums.DocumentType;
import com.theron.wallet.enums.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponse {

    private UUID id;

    @Schema(description = "Legal / registered company name")
    private String legalName;

    @Schema(description = "Trade name")
    private String tradeName;

    @Schema(description = "Normalized CPF or CNPJ (digits only)")
    private String document;

    private DocumentType documentType;

    private OrganizationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
