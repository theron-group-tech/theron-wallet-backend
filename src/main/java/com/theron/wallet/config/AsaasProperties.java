package com.theron.wallet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "asaas.api")
public class AsaasProperties {

    private String baseUrl;
    private String key;
    private String webhookToken;
    private String webhookUrl;
    private TimeoutProperties timeout = new TimeoutProperties();
    private RetryProperties retry = new RetryProperties();

    @Getter
    @Setter
    public static class TimeoutProperties {
        private int connect = 5000;
        private int read = 10000;
    }

    @Getter
    @Setter
    public static class RetryProperties {
        private int maxAttempts = 3;
    }
}
