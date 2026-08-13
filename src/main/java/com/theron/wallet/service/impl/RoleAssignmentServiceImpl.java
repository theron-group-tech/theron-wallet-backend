package com.theron.wallet.service.impl;

import com.theron.wallet.entity.MembershipRole;
import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.entity.Role;
import com.theron.wallet.enums.RoleCode;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.RoleRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.RoleAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final AuthorizationService authorizationService;
    private final OrganizationMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public List<String> replaceRoles(
            UUID actorUserId, UUID organizationId, UUID targetUserId, List<String> roleCodes) {
        authorizationService.requirePermission(organizationId, actorUserId, PermissionCodes.MEMBERS_MANAGE);

        List<String> normalized = normalizeRoleCodes(roleCodes);
        if (normalized.contains(RoleCode.OWNER.name())) {
            List<String> actorRoles = authorizationService.listRoles(organizationId, actorUserId);
            if (!actorRoles.contains(RoleCode.OWNER.name())) {
                throw new ForbiddenException("Only OWNER can grant OWNER role");
            }
        }

        assignRolesInternal(organizationId, targetUserId, normalized);
        log.info("Roles replaced: organizationId={}, targetUserId={}, roles={}",
                organizationId, targetUserId, normalized);
        return authorizationService.listRoles(organizationId, targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listRoles(UUID actorUserId, UUID organizationId, UUID targetUserId) {
        authorizeReadOrSelf(actorUserId, organizationId, targetUserId);
        return authorizationService.listRoles(organizationId, targetUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listPermissions(UUID actorUserId, UUID organizationId, UUID targetUserId) {
        authorizeReadOrSelf(actorUserId, organizationId, targetUserId);
        return authorizationService.listPermissions(organizationId, targetUserId);
    }

    @Override
    @Transactional
    public void assignRolesInternal(UUID organizationId, UUID userId, List<String> roleCodes) {
        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OrganizationMembership", "organizationId+userId",
                        organizationId + "/" + userId));

        List<String> normalized = normalizeRoleCodes(roleCodes);
        membershipRoleRepository.deleteByMembershipId(membership.getId());
        // flush so unique constraint allows re-insert in same TX
        membershipRoleRepository.flush();

        for (String code : normalized) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new InvalidRequestException("Unknown role code: " + code));
            membershipRoleRepository.save(MembershipRole.builder()
                    .membership(membership)
                    .role(role)
                    .build());
        }
    }

    private void authorizeReadOrSelf(UUID actorUserId, UUID organizationId, UUID targetUserId) {
        if (actorUserId.equals(targetUserId)) {
            authorizationService.requirePermission(organizationId, actorUserId, PermissionCodes.ORGANIZATION_READ);
            return;
        }
        authorizationService.requirePermission(organizationId, actorUserId, PermissionCodes.MEMBERS_READ);
    }

    private static List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new InvalidRequestException("At least one role code is required");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : roleCodes) {
            if (raw == null || raw.isBlank()) {
                throw new InvalidRequestException("Role code must not be blank");
            }
            String code = raw.trim().toUpperCase(Locale.ROOT);
            try {
                RoleCode.valueOf(code);
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("Unknown role code: " + code);
            }
            unique.add(code);
        }
        return new ArrayList<>(unique);
    }
}
