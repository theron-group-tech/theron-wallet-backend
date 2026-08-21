package com.theron.wallet.integration;

/**
 * Helpers to parse Asaas error JSON bodies into user-facing descriptions.
 * Format typically: {"errors":[{"code":"...","description":"..."}]}
 */
public final class AsaasErrorBodies {

    private AsaasErrorBodies() {
    }

    public static String extractFirstDescription(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return null;
        }
        try {
            int start = errorBody.indexOf("\"description\":\"");
            if (start < 0) {
                return null;
            }
            start += 15;
            int end = errorBody.indexOf("\"", start);
            if (end < 0) {
                return null;
            }
            return errorBody.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds a short statusReason / UI message from an Asaas API failure.
     */
    public static String formatFailureReason(Integer httpStatus, String errorBody, String fallbackMessage) {
        String description = extractFirstDescription(errorBody);
        if (description != null && !description.isBlank()) {
            String prefix = httpStatus != null
                    ? "Asaas validation error (" + httpStatus + "): "
                    : "Asaas validation error: ";
            return AsaasSecretRedactor.redact(prefix + description);
        }
        String base = fallbackMessage != null && !fallbackMessage.isBlank()
                ? fallbackMessage
                : "Payment provider error. Please try again later.";
        if (httpStatus != null) {
            return AsaasSecretRedactor.redact("Asaas API call failed (" + httpStatus + "): " + base);
        }
        return AsaasSecretRedactor.redact("Asaas API call failed: " + base);
    }
}
