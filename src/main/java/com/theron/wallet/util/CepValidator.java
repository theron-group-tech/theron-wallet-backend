package com.theron.wallet.util;

import com.theron.wallet.exception.InvalidRequestException;

public final class CepValidator {

    private static final String CEP_PATTERN = "^\\d{8}$";

    private CepValidator() {
    }

    public static String normalize(String cep) {
        if (cep == null) {
            return null;
        }
        return cep.replaceAll("\\D", "");
    }

    public static void requireValid(String cep) {
        String normalized = normalize(cep);
        if (normalized == null || !normalized.matches(CEP_PATTERN)) {
            throw new InvalidRequestException("postalCode must be a valid 8-digit CEP");
        }
    }
}
