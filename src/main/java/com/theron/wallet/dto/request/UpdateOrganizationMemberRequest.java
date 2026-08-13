package com.theron.wallet.dto.request;

import com.theron.wallet.enums.MembershipStatus;
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
@Schema(description = "Update organization membership status")
public class UpdateOrganizationMemberRequest {

    @NotNull
    @Schema(allowableValues = {"ACTIVE", "INVITED", "SUSPENDED", "REMOVED"})
    private MembershipStatus status;
}
