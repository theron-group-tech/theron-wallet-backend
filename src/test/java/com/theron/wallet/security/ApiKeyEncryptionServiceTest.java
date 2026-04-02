package com.theron.wallet.security;

import com.theron.wallet.config.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyEncryptionServiceTest {

    private ApiKeyEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        EncryptionProperties properties = new EncryptionProperties();
        // Valid 32-byte key encoded as base64
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i + 1);
        }
        properties.setAesKey(Base64.getEncoder().encodeToString(key));
        encryptionService = new ApiKeyEncryptionService(properties);
    }

    @Nested
    @DisplayName("encrypt() + decrypt() round-trip")
    class RoundTripTests {

        @Test
        @DisplayName("should encrypt and decrypt an API key back to the original plaintext")
        void shouldRoundTripApiKey() {
            String originalKey = "asaas_api_key_abc123xyz789";
            byte[] encrypted = encryptionService.encrypt(originalKey);
            String decrypted = encryptionService.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(originalKey);
        }

        @Test
        @DisplayName("should produce different ciphertext for the same plaintext (random IV)")
        void shouldProduceDifferentCiphertextEachTime() {
            String apiKey = "test_api_key_repeated";
            byte[] first = encryptionService.encrypt(apiKey);
            byte[] second = encryptionService.encrypt(apiKey);

            assertThat(first).isNotEqualTo(second);

            // But both should decrypt to the same value
            assertThat(encryptionService.decrypt(first)).isEqualTo(apiKey);
            assertThat(encryptionService.decrypt(second)).isEqualTo(apiKey);
        }

        @Test
        @DisplayName("should handle long API keys correctly")
        void shouldHandleLongKeys() {
            String longKey = "a".repeat(500);
            byte[] encrypted = encryptionService.encrypt(longKey);
            String decrypted = encryptionService.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(longKey);
        }

        @Test
        @DisplayName("should handle special characters in API keys")
        void shouldHandleSpecialCharacters() {
            String specialKey = "key+with/special=chars&more!@#$%";
            byte[] encrypted = encryptionService.encrypt(specialKey);
            String decrypted = encryptionService.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(specialKey);
        }
    }

    @Nested
    @DisplayName("decrypt() with corrupted data")
    class CorruptionTests {

        @Test
        @DisplayName("should throw when decrypting tampered ciphertext")
        void shouldThrowOnTamperedData() {
            String apiKey = "valid_key";
            byte[] encrypted = encryptionService.encrypt(apiKey);

            // Tamper with the ciphertext portion (after the 12-byte IV)
            encrypted[15] ^= 0xFF;

            assertThatThrownBy(() -> encryptionService.decrypt(encrypted))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to decrypt API key");
        }

        @Test
        @DisplayName("should throw when decrypting truncated data")
        void shouldThrowOnTruncatedData() {
            byte[] tooShort = new byte[5];

            assertThatThrownBy(() -> encryptionService.decrypt(tooShort))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("should reject a key that is not 32 bytes")
        void shouldRejectInvalidKeyLength() {
            EncryptionProperties badProps = new EncryptionProperties();
            byte[] shortKey = new byte[16];
            badProps.setAesKey(Base64.getEncoder().encodeToString(shortKey));

            assertThatThrownBy(() -> new ApiKeyEncryptionService(badProps))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES key must be exactly 32 bytes");
        }
    }
}
