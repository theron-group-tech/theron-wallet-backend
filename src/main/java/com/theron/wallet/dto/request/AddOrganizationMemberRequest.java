package com.theron.wallet.dto.request;

import com.theron.wallet.enums.MembershipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Add an existing user as organization member")
public class AddOrganizationMemberRequest {

    @NotNull
    @Schema(description = "Existing user id")
    private UUID userId;

    @Schema(description = "Membership status (default ACTIVE)", allowableValues = {"ACTIVE", "INVITED", "SUSPENDED"})
    private MembershipStatus status;
}
