package com.theron.wallet.dto.response;

import com.theron.wallet.enums.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMembershipResponse {

    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private List<String> roleCodes;
    private MembershipStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
