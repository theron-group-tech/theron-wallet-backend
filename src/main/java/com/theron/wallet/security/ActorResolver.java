package com.theron.wallet.security;

import com.theron.wallet.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ActorResolver {

    public UUID requireProductUserId(UUID headerFallback) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.isProductUser() && principal.getUserId() != null) {
            return principal.getUserId();
        }
        if (headerFallback != null) {
            return headerFallback;
        }
        throw new UnauthorizedException("Authentication required");
    }

    public UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
