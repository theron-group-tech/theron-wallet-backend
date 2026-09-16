package com.theron.wallet.security;

import com.theron.wallet.entity.Account;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.UnauthorizedException;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/**
 * Unified actor for product USER and B2B OAuth CLIENT.
 */
@Getter
public final class Actor {

    public enum Kind {
        USER,
        CLIENT
    }

    private final Kind kind;
    private final UUID userId;
    private final ClientPrincipal client;

    private Actor(Kind kind, UUID userId, ClientPrincipal client) {
        this.kind = kind;
        this.userId = userId;
        this.client = client;
    }

    public static Actor user(UUID userId) {
        return new Actor(Kind.USER, Objects.requireNonNull(userId), null);
    }

    public static Actor client(ClientPrincipal client) {
        return new Actor(Kind.CLIENT, null, Objects.requireNonNull(client));
    }

    public boolean isUser() {
        return kind == Kind.USER;
    }

    public boolean isClient() {
        return kind == Kind.CLIENT;
    }

    /**
     * User id for DB FKs ({@code createdBy}) and human audit.
     * For CLIENT, uses the Account owner (technical attribution).
     */
    public UUID technicalUserId(Account account) {
        if (isUser()) {
            return userId;
        }
        if (account == null || account.getOwnerUser() == null) {
            throw new ForbiddenException("Account has no owner user for client attribution");
        }
        return account.getOwnerUser().getId();
    }

    public UUID requireUserId() {
        if (!isUser() || userId == null) {
            throw new UnauthorizedException("Product user authentication required");
        }
        return userId;
    }

    public String auditLabel() {
        if (isUser()) {
            return "user:" + userId;
        }
        return "client:" + client.getPublicClientId();
    }

    /**
     * Account bound to this actor for B2B charges (CLIENT 1:1). USER must use resource-scoped APIs.
     */
    public UUID requireBoundAccountId() {
        if (!isClient() || client == null) {
            throw new UnauthorizedException("OAuth client credentials required for bound-account operations");
        }
        return client.requireBoundAccountId();
    }

    public UUID requireOrganizationId() {
        if (!isClient() || client == null) {
            throw new UnauthorizedException("OAuth client credentials required");
        }
        return client.getOrganizationId();
    }
}
