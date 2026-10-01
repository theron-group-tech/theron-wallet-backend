package com.theron.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Vem Comigo-specific Theron platform commission: percentage of the issuer residual
 * (charge value minus counterparty splits), not of the full charge amount.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "platform.vem-comigo")
public class VemComigoSplitProperties {

    private UUID organizationId = UUID.fromString("565d0a47-6cd8-441a-b1de-766f042cd6d5");

    /** Theron commission as percent of issuer residual (e.g. 20 = 20%). */
    private BigDecimal commissionPercent = new BigDecimal("20");
}
