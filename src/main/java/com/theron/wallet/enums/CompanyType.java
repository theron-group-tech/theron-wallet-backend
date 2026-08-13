package com.theron.wallet.enums;

/**
 * Asaas company type for Pessoa Jurídica (CNPJ) subaccounts.
 * Values mirror Asaas POST /v3/accounts {@code companyType}.
 */
public enum CompanyType {
    MEI,
    LIMITED,
    INDIVIDUAL,
    ASSOCIATION
}
