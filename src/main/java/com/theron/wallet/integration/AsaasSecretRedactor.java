package com.theron.wallet.integration;

public final class AsaasSecretRedactor {

    private AsaasSecretRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text
                .replaceAll("(?i)\\$aact_[A-Za-z0-9+/=_-]+", "[REDACTED]")
                .replaceAll("(?i)(access_token|apiKey|api_key)\\s*([:=]\\s*)[^\\s\"',}]+", "$1$2[REDACTED]");
    }
}
