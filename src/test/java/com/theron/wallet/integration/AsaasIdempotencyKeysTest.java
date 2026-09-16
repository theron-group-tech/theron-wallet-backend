package com.theron.wallet.integration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsaasIdempotencyKeysTest {

    @Test
    void forHeader_nullOrBlank_returnsNull() {
        assertNull(AsaasIdempotencyKeys.forHeader(null));
        assertNull(AsaasIdempotencyKeys.forHeader("  "));
    }

    @Test
    void forHeader_shortKey_unchanged() {
        String key = "charge-abc-123";
        assertEquals(key, AsaasIdempotencyKeys.forHeader(key));
    }

    @Test
    void forHeader_longKey_hashedTo48Stable() {
        String longKey = "charge:" + UUID.randomUUID() + ":postman-" + UUID.randomUUID();
        assertTrue(longKey.length() > AsaasIdempotencyKeys.MAX_LENGTH);

        String first = AsaasIdempotencyKeys.forHeader(longKey);
        String second = AsaasIdempotencyKeys.forHeader(longKey);

        assertEquals(AsaasIdempotencyKeys.MAX_LENGTH, first.length());
        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{48}"));
    }

    @Test
    void forCharge_withExternalReference_is48AndDeterministic() {
        UUID accountId = UUID.fromString("b0cf0d84-edf2-42f4-9896-f46d4f2cbab1");
        String ext = "postman-" + UUID.randomUUID();

        String first = AsaasIdempotencyKeys.forCharge(accountId, ext);
        String second = AsaasIdempotencyKeys.forCharge(accountId, ext);

        assertEquals(AsaasIdempotencyKeys.MAX_LENGTH, first.length());
        assertEquals(first, second);
        assertTrue(first.startsWith("c"));
    }

    @Test
    void forCharge_withoutExternalReference_isUuidWithoutDashes() {
        String key = AsaasIdempotencyKeys.forCharge(UUID.randomUUID(), null);
        assertEquals(32, key.length());
        assertTrue(key.matches("[0-9a-f]{32}"));
    }
}
