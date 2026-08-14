package com.theron.wallet.security;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserPrincipal {

    public static final String PRINCIPAL_USER = "USER";
    public static final String PRINCIPAL_ADMIN = "ADMIN";

    private final String principalType;
    private final UUID userId;
    private final UUID sessionId;
    private final UUID adminId;
    private final String email;
    private final String name;
    private final String role;

    public boolean isProductUser() {
        return PRINCIPAL_USER.equals(principalType);
    }

    public boolean isAdmin() {
        return PRINCIPAL_ADMIN.equals(principalType);
    }
}
