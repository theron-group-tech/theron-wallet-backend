package com.theron.wallet.security;

import com.theron.wallet.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActorResolver {

    public UUID requireProductUserId() {
        UserPrincipal principal = currentPrincipal();

        if (principal != null
                && principal.isProductUser()
                && principal.getUserId() != null) {
            return principal.getUserId();
        }

        throw new UnauthorizedException("Product user authentication required");
    }

    public UserPrincipal requirePrincipal() {
        UserPrincipal principal = currentPrincipal();

        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }

        return principal;
    }

    public UserPrincipal currentPrincipal() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }

        return null;
    }

    public boolean isAdmin() {
        UserPrincipal principal = currentPrincipal();
        return principal != null && principal.isAdmin();
    }
}