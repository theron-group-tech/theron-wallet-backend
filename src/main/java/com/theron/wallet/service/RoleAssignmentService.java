package com.theron.wallet.service;

import java.util.List;
import java.util.UUID;

public interface RoleAssignmentService {

    /**
     * Replaces all roles for the target membership. Actor must have members.manage.
     * Only OWNER may grant OWNER. Never trusts client-sent organizationId outside the path.
     */
    List<String> replaceRoles(UUID actorUserId, UUID organizationId, UUID targetUserId, List<String> roleCodes);

    List<String> listRoles(UUID actorUserId, UUID organizationId, UUID targetUserId);

    List<String> listPermissions(UUID actorUserId, UUID organizationId, UUID targetUserId);

    /** Internal helper for tests/bootstrap — assigns roles without actor checks. */
    void assignRolesInternal(UUID organizationId, UUID userId, List<String> roleCodes);
}
