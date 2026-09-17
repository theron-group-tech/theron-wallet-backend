package com.theron.wallet.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * High-entropy OAuth client secrets / Theron API Keys hashed with SHA-256 (not BCrypt).
 */
@Component
public class OauthClientSecretHasher {

    private static final int SECRET_BYTES = 32;
    private static final int CLIENT_ID_BYTES = 16;
    private static final int API_KEY_RANDOM_BYTES = 24;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generatePlainSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Theron API Key format: {@code tk_sandbox_<random>} or {@code tk_live_<random>}.
     */
    public String generateTheronApiKey(boolean live) {
        byte[] bytes = new byte[API_KEY_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return (live ? "tk_live_" : "tk_sandbox_") + random;
    }

    public String apiKeyPrefix(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.length() < 16) {
            return plainApiKey;
        }
        // Keep env prefix + enough entropy for display without revealing the secret
        int keep = Math.min(plainApiKey.length(), 20);
        return plainApiKey.substring(0, keep);
    }

    public boolean isTheronApiKey(String bearer) {
        return bearer != null
                && (bearer.startsWith("tk_sandbox_") || bearer.startsWith("tk_live_"));
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
