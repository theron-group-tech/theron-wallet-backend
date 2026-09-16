package com.theron.wallet.util;

import com.theron.wallet.enums.DocumentType;

public final class AsaasDocumentRules {

    public static final String CNPJ_REQUIRED_MESSAGE =
            "Asaas subaccounts require CNPJ (14 digits); CPF is not allowed";

    private AsaasDocumentRules() {
    }

    public static String normalize(String document) {
        if (document == null) {
            return null;
        }
        return document.replaceAll("\\D", "");
    }

    public static boolean isCnpj(String normalizedDocument) {
        return normalizedDocument != null && normalizedDocument.length() == 14;
    }

    public static void requireCnpj(String document, String fieldName) {
        String normalized = normalize(document);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() == 11) {
            throw new IllegalArgumentException(CNPJ_REQUIRED_MESSAGE);
        }
        if (normalized.length() != 14) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 14 digits (CNPJ)");
        }
    }

    public static void requireCpf(String document, String fieldName) {
        String normalized = normalize(document);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() != 11) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 11 digits (CPF)");
        }
    }

    public static void requireCpfOrCnpj(String document, String fieldName) {
        String normalized = normalize(document);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (normalized.length() != 11 && normalized.length() != 14) {
            throw new IllegalArgumentException(fieldName + " must be a valid CPF (11) or CNPJ (14)");
        }
    }

    public static void requireOrganizationCnpj(String document, DocumentType documentType) {
        if (documentType == DocumentType.CPF) {
            throw new IllegalArgumentException(
                    "Organization document must be CNPJ for Asaas BaaS integration");
        }
        requireCnpj(document, "document");
    }
}
