package com.theron.wallet.service.impl;

import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.OrganizationMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final MembershipRoleRepository membershipRoleRepository;
    private final OrganizationMembershipService membershipService;

    @Override
    @Transactional(readOnly = true)
    public void requirePermission(UUID organizationId, UUID userId, String permissionCode) {
        // Always resolve from DB; never trust client-sent roles/permissions.
        membershipService.assertActiveMembership(organizationId, userId);
        if (membershipRoleRepository.countPermission(organizationId, userId, permissionCode) == 0) {
            throw new ForbiddenException(
                    "Missing permission '" + permissionCode + "' in organization " + organizationId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID organizationId, UUID userId, String permissionCode) {
        try {
            membershipService.assertActiveMembership(organizationId, userId);
        } catch (ForbiddenException ex) {
            return false;
        }
        return membershipRoleRepository.countPermission(organizationId, userId, permissionCode) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listPermissions(UUID organizationId, UUID userId) {
        membershipService.assertActiveMembership(organizationId, userId);
        return membershipRoleRepository.findPermissionCodesByOrganizationAndUser(organizationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listRoles(UUID organizationId, UUID userId) {
        membershipService.assertActiveMembership(organizationId, userId);
        return membershipRoleRepository.findRoleCodesByOrganizationAndUser(organizationId, userId);
    }
}
