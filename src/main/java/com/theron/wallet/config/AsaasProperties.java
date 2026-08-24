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
    private String masterWalletId;
    /**
     * When true (default in sandbox), call {@code POST /accounts/{id}/approve} after subaccount creation.
     */
    private Boolean autoApproveSubaccounts;
    private SubaccountDefaults subaccountDefaults = new SubaccountDefaults();
    private TimeoutProperties timeout = new TimeoutProperties();
    private RetryProperties retry = new RetryProperties();

    public boolean isSandbox() {
        return baseUrl != null && baseUrl.toLowerCase().contains("sandbox");
    }

    /**
     * Sandbox-only approve; production Asaas has no approve endpoint — always false outside sandbox.
     */
    public boolean isAutoApproveSubaccounts() {
        if (!isSandbox()) {
            return false;
        }
        return autoApproveSubaccounts == null || autoApproveSubaccounts;
    }

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

    @Getter
    @Setter
    public static class SubaccountDefaults {
        private String mobile = "11999999999";
        private String postalCode = "01310100";
        private String address = "Avenida Paulista";
        private String addressNumber = "1000";
        private String province = "Bela Vista";
        private String incomeValue = "10000";
        private String companyType = "LIMITED";
        private String birthDate = "1990-01-01";
    }
}
