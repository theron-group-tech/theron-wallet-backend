package com.theron.wallet.util;

public final class CpfCnpjValidator {

    private CpfCnpjValidator() {
    }

    public static boolean isValidCpf(String cpf) {
        String digits = AsaasDocumentRules.normalize(cpf);
        if (digits == null || digits.length() != 11 || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) && checkDigit(digits, 10);
    }

    public static boolean isValidCnpj(String cnpj) {
        String digits = AsaasDocumentRules.normalize(cnpj);
        if (digits == null || digits.length() != 14 || digits.chars().distinct().count() == 1) {
            return false;
        }
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return checkCnpjDigit(digits, 12, weights1) && checkCnpjDigit(digits, 13, weights2);
    }

    private static boolean checkDigit(String digits, int position) {
        int sum = 0;
        for (int i = 0; i < position; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (position + 1 - i);
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return Character.getNumericValue(digits.charAt(position)) == expected;
    }

    private static boolean checkCnpjDigit(String digits, int position, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights[i];
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return Character.getNumericValue(digits.charAt(position)) == expected;
    }
}
