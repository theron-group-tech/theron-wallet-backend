package com.theron.wallet.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * High-entropy OAuth client secrets hashed with SHA-256 (not BCrypt).
 */
@Component
public class OauthClientSecretHasher {

    private static final int SECRET_BYTES = 32;
    private static final int CLIENT_ID_BYTES = 16;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generatePlainSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generatePublicClientId() {
        byte[] bytes = new byte[CLIENT_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return "tc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String plainSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plainSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public boolean matches(String plainSecret, String storedHash) {
        if (plainSecret == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(plainSecret).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
