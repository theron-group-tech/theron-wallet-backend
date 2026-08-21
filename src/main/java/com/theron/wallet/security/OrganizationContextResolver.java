package com.theron.wallet.security;

import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationContextResolver {

    private final OrganizationMembershipRepository membershipRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public UUID requireSingleOrganizationId(UUID userId) {
        List<OrganizationMembership> memberships =
                membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
        if (memberships.isEmpty()) {
            throw new ForbiddenException("No organization membership");
        }
        if (memberships.size() > 1) {
            throw new DuplicateResourceException(
                    "User belongs to multiple organizations; use /api/v1/organizations/{id} paths");
        }
        return memberships.getFirst().getOrganization().getId();
    }

    @Transactional(readOnly = true)
    public void requireOwner(UUID organizationId, UUID userId) {
        List<String> roles = authorizationService.listRoles(organizationId, userId);
        if (!roles.contains(RoleCode.OWNER.name())) {
            throw new ForbiddenException("Organization administrator role required");
        }
    }

    /**
     * Financial isolation: nobody sees another user's wallet/PIX/statement.
     * Always false — OWNER admin scope is members/PaymentOrders only.
     */
    @Transactional(readOnly = true)
    public boolean isOrgWideViewer(UUID organizationId, UUID userId) {
        return false;
    }
}
