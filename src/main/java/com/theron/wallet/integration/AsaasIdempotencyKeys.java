package com.theron.wallet.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Asaas rejects {@code Idempotency-Key} values longer than 48 characters.
 */
public final class AsaasIdempotencyKeys {

    public static final int MAX_LENGTH = 48;

    private AsaasIdempotencyKeys() {
    }

    /**
     * Returns a header-safe key: unchanged when {@code ≤ 48}, otherwise SHA-256 hex truncated to 48
     * (deterministic for retries).
     */
    public static String forHeader(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() <= MAX_LENGTH) {
            return trimmed;
        }
        return sha256Hex(trimmed).substring(0, MAX_LENGTH);
    }

    /**
     * Deterministic charge key for Asaas ({@code c} + 47 hex = 48 chars).
     */
    public static String forCharge(UUID accountId, String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String material = accountId + "|" + externalReference.trim();
        return "c" + sha256Hex(material).substring(0, MAX_LENGTH - 1);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
