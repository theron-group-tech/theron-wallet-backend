package com.theron.wallet.security;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class ClientPrincipal {

    public static final String PRINCIPAL_CLIENT = "CLIENT";

    private final UUID oauthClientId;
    private final String publicClientId;
    private final UUID organizationId;
    private final String name;
    @Builder.Default
    private final Set<String> scopes = Collections.emptySet();
    @Builder.Default
    private final Set<UUID> allowedAccountIds = Collections.emptySet();

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    public boolean canAccessAccount(UUID accountId) {
        return accountId != null && allowedAccountIds != null && allowedAccountIds.contains(accountId);
    }

    /**
     * OAuth clients are bound 1:1 to a single Account — returns that Account id.
     */
    public UUID requireBoundAccountId() {
        if (allowedAccountIds == null || allowedAccountIds.size() != 1) {
            throw new com.theron.wallet.exception.ForbiddenException(
                    "OAuth client must be bound to exactly one Account");
        }
        return allowedAccountIds.iterator().next();
    }
}
