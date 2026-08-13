package com.theron.wallet.security;

import com.theron.wallet.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ensures path-scoped organization matches the organization that owns a resource.
 * Used to reject forged accountId/walletId (and similar) until full ownership models exist.
 */
@Component
public class TenantAccessGuard {

    public void requireSameOrganization(UUID pathOrganizationId, UUID resourceOrganizationId) {
        if (pathOrganizationId == null || resourceOrganizationId == null
                || !pathOrganizationId.equals(resourceOrganizationId)) {
            throw new ForbiddenException("Resource does not belong to the requested organization");
        }
    }

    /**
     * Convenience for resources identified by id + owning organization.
     */
    public void requireResourceInOrganization(
            UUID pathOrganizationId,
            UUID resourceId,
            UUID resourceOrganizationId) {
        if (resourceId == null) {
            throw new ForbiddenException("Resource id is required");
        }
        requireSameOrganization(pathOrganizationId, resourceOrganizationId);
    }
}
