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
}
