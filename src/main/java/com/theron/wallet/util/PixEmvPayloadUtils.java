package com.theron.wallet.util;

import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeRequest;

import java.math.BigDecimal;

/**
 * Parses PIX EMV copia-e-cola payloads (BR Code).
 */
public final class PixEmvPayloadUtils {

    private PixEmvPayloadUtils() {
    }

    /**
     * Reads tag 54 (transaction amount) from a PIX EMV payload.
     *
     * @return parsed amount or null if tag 54 is absent or payload is invalid
     */
    public static BigDecimal parseTransactionAmount(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String trimmed = payload.trim();
        int index = 0;
        while (index + 4 <= trimmed.length()) {
            String tag = trimmed.substring(index, index + 2);
            int length;
            try {
                length = Integer.parseInt(trimmed.substring(index + 2, index + 4));
            } catch (NumberFormatException ex) {
                return null;
            }
            int valueStart = index + 4;
            int valueEnd = valueStart + length;
            if (valueEnd > trimmed.length()) {
                return null;
            }
            String value = trimmed.substring(valueStart, valueEnd);
            if ("54".equals(tag)) {
                try {
                    return new BigDecimal(value);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            if ("62".equals(tag) || "26".equals(tag)) {
                BigDecimal nested = parseTransactionAmount(value);
                if (nested != null) {
                    return nested;
                }
            }
            index = valueEnd;
        }
        return null;
    }

    public static BigDecimal resolveQrCodeValue(
            BigDecimal asaasValue, BigDecimal requestValue, String payload) {
        if (asaasValue != null && asaasValue.compareTo(BigDecimal.ZERO) > 0) {
            return asaasValue;
        }
        if (requestValue != null && requestValue.compareTo(BigDecimal.ZERO) > 0) {
            return requestValue;
        }
        return parseTransactionAmount(payload);
    }

    public static AsaasPixStaticQrCodeRequest buildStaticQrCodeRequest(BigDecimal value, String description) {
        boolean fixedValue = value != null && value.compareTo(BigDecimal.ZERO) > 0;
        return AsaasPixStaticQrCodeRequest.builder()
                .value(value)
                .description(description)
                .format("ALL")
                .allowsMultiplePayments(fixedValue ? Boolean.FALSE : Boolean.TRUE)
                .build();
    }
}
