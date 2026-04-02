package com.theron.wallet.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookTokenGeneratorTest {

    private final WebhookTokenGenerator generator = new WebhookTokenGenerator();

    @Test
    @DisplayName("should generate a 64-character hex token (32 bytes)")
    void shouldGenerateCorrectLength() {
        String token = generator.generate();

        assertThat(token).hasSize(64);
        assertThat(token).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("should generate unique tokens on successive calls")
    void shouldGenerateUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(generator.generate());
        }
        assertThat(tokens).hasSize(100);
    }
}
