package com.theron.wallet.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates cryptographically secure webhook tokens for subaccount identification.
 * Each subaccount gets a unique token used to route inbound webhooks.
 */
@Component
public class WebhookTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return HexFormat.of().formatHex(tokenBytes);
    }
}
