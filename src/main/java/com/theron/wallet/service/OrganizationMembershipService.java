package com.theron.wallet.service;

import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.UpdateOrganizationMemberRequest;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.enums.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrganizationMembershipService {

    OrganizationMembershipResponse addMember(UUID organizationId, AddOrganizationMemberRequest request);

    Page<OrganizationMembershipResponse> listMembers(
            UUID organizationId, MembershipStatus status, Pageable pageable);

    OrganizationMembershipResponse updateMember(
            UUID organizationId, UUID userId, UpdateOrganizationMemberRequest request);

    OrganizationMembershipResponse removeMember(UUID organizationId, UUID userId);

    /**
     * Ensures the user has ACTIVE membership in the organization.
     * Throws ForbiddenException if missing or not ACTIVE.
     */
    void assertActiveMembership(UUID organizationId, UUID userId);
}
