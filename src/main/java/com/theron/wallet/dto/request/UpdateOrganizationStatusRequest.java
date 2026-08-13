package com.theron.wallet.dto.request;

import com.theron.wallet.enums.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body to change organization status")
public class UpdateOrganizationStatusRequest {

    @NotNull
    @Schema(description = "New status", example = "SUSPENDED", allowableValues = {"ACTIVE", "SUSPENDED", "BLOCKED"})
    private OrganizationStatus status;
}
