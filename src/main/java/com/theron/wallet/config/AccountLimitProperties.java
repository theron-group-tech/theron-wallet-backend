package com.theron.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "theron.account-limit")
public class AccountLimitProperties {

    /** Default max amount per PIX/operation when provisioning account_limit. */
    private BigDecimal defaultMaxOperation = new BigDecimal("5000.00");

    /** Default daily PIX spend cap when provisioning account_limit. */
    private BigDecimal defaultDaily = new BigDecimal("10000.00");
}
