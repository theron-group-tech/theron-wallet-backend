package com.theron.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vem Comigo-specific settings: residual platform commission and outbound partner webhooks.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "platform.vem-comigo")
public class VemComigoSplitProperties {

    private UUID organizationId = UUID.fromString("565d0a47-6cd8-441a-b1de-766f042cd6d5");

    /** Theron commission as percent of issuer residual (e.g. 20 = 20%). */
    private BigDecimal commissionPercent = new BigDecimal("20");

    /** Partner webhook endpoint (Theron → Vem Comigo). Empty disables delivery. */
    private String webhookUrl = "";

    /** HMAC-SHA256 secret for {@code X-Theron-Signature}. Empty disables delivery. */
    private String webhookSecret = "";

    private boolean webhookEnabled = true;
}
