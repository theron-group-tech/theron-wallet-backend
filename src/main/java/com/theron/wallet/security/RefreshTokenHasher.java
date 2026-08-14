package com.theron.wallet.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class RefreshTokenHasher {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generatePlainToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String plainToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
