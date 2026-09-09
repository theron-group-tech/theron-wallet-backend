package com.theron.wallet.security;

import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActorResolver {

    public Actor requireActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal user) {
            if (user.isProductUser() && user.getUserId() != null) {
                return Actor.user(user.getUserId());
            }
            if (user.isAdmin()) {
                throw new ForbiddenException("Platform administrator cannot access product resources as actor");
            }
        }
        if (principal instanceof ClientPrincipal client) {
            return Actor.client(client);
        }
        throw new UnauthorizedException("Authentication required");
    }

    public UUID requireProductUserId() {
        UserPrincipal principal = currentUserPrincipal();

        if (principal != null
                && principal.isProductUser()
                && principal.getUserId() != null) {
            return principal.getUserId();
        }

        throw new UnauthorizedException("Product user authentication required");
    }

    public UserPrincipal requirePrincipal() {
        UserPrincipal principal = currentUserPrincipal();

        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }

        return principal;
    }

    public UserPrincipal requireAdmin() {
        UserPrincipal principal = currentUserPrincipal();
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        if (!principal.isAdmin() || principal.getAdminId() == null) {
            throw new ForbiddenException("Platform administrator authentication required");
        }
        return principal;
    }

    public UserPrincipal currentPrincipal() {
        return currentUserPrincipal();
    }

    public ClientPrincipal currentClientPrincipal() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof ClientPrincipal principal) {
            return principal;
        }
        return null;
    }

    public boolean isAdmin() {
        UserPrincipal principal = currentUserPrincipal();
        return principal != null && principal.isAdmin();
    }

    private UserPrincipal currentUserPrincipal() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }

        return null;
    }
}
