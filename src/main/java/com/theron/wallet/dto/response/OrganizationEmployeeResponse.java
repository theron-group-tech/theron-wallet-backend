package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.MembershipStatus;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationEmployeeResponse {

    private UUID userId;
    private String name;
    private String email;
    private UUID organizationId;
    private MembershipStatus membershipStatus;
    private AccountResponse account;
    private AsaasBindResponse asaasBind;
}
