package com.theron.wallet.dto.request;

import com.theron.wallet.enums.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update for an organization. Document is immutable. Omit null fields.")
public class UpdateOrganizationRequest {

    @Size(max = 255)
    @Schema(description = "Legal / registered company name", example = "Acme Tecnologia LTDA")
    private String legalName;

    @Size(max = 255)
    @Schema(description = "Trade name", example = "Acme Pay")
    private String tradeName;

    @Schema(description = "Organization status", example = "SUSPENDED", allowableValues = {"ACTIVE", "SUSPENDED", "BLOCKED"})
    private OrganizationStatus status;
}
