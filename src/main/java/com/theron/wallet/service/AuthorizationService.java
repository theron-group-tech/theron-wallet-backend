package com.theron.wallet.service;

import java.util.List;
import java.util.UUID;

public interface AuthorizationService {

    void requirePermission(UUID organizationId, UUID userId, String permissionCode);

    boolean hasPermission(UUID organizationId, UUID userId, String permissionCode);

    List<String> listPermissions(UUID organizationId, UUID userId);

    List<String> listRoles(UUID organizationId, UUID userId);
}
