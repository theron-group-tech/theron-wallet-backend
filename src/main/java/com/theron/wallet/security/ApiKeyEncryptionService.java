package com.theron.wallet.security;

import com.theron.wallet.config.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for Asaas API keys.
 * Format: [12-byte IV][ciphertext + 16-byte GCM tag]
 */
@Slf4j
@Service
public class ApiKeyEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    public ApiKeyEncryptionService(EncryptionProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getAesKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("AES key must be exactly 32 bytes (256-bit). Got: " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (Exception ex) {
            log.error("Encryption failed: {}", ex.getMessage());
            throw new IllegalStateException("Failed to encrypt API key", ex);
        }
    }

    public String decrypt(byte[] encryptedData) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Decryption failed: {}", ex.getMessage());
            throw new IllegalStateException("Failed to decrypt API key", ex);
        }
    }
}
