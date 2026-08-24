package com.theron.wallet.enums;

import com.theron.wallet.exception.InvalidRequestException;

public enum PixKeyType {
    CPF, CNPJ, EMAIL, PHONE, EVP;

    public static final String PROVIDER_CREATE_UNSUPPORTED_MESSAGE =
            "Asaas API only creates random PIX keys (EVP). CPF, CNPJ, EMAIL and PHONE cannot be created via API.";

    /**
     * Asaas only allows creating random (EVP) keys via API.
     * CPF, CNPJ, EMAIL and PHONE remain valid as destination key types.
     */
    public boolean isCreatableViaProvider() {
        return this == EVP;
    }

    public void requireCreatableViaProvider() {
        if (!isCreatableViaProvider()) {
            throw new InvalidRequestException(PROVIDER_CREATE_UNSUPPORTED_MESSAGE);
        }
    }
}