package com.theron.wallet.dto.response;

import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizationResponse {

    private UUID organizationId;
    private String legalName;
    private String tradeName;
    private OrganizationStatus organizationStatus;
    private MembershipStatus membershipStatus;
}
