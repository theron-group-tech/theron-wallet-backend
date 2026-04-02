package com.theron.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {

    /**
     * Base64-encoded AES-256 key (32 bytes).
     * Generate with: openssl rand -base64 32
     */
    private String aesKey;
}
