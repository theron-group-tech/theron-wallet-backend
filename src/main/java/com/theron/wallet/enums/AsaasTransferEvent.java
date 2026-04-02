package com.theron.wallet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsaasTransferEvent {
    TRANSFER_CREATED("TRANSFER_CREATED"),
    TRANSFER_PENDING("TRANSFER_PENDING"),
    TRANSFER_IN_BANK_PROCESSING("TRANSFER_IN_BANK_PROCESSING"),
    TRANSFER_BLOCKED("TRANSFER_BLOCKED"),
    TRANSFER_DONE("TRANSFER_DONE"),
    TRANSFER_FAILED("TRANSFER_FAILED"),
    TRANSFER_CANCELLED("TRANSFER_CANCELLED");

    private final String value;

    public boolean isConfirmation() {
        return this == TRANSFER_DONE;
    }

    public boolean isFailure() {
        return this == TRANSFER_FAILED || this == TRANSFER_CANCELLED || this == TRANSFER_BLOCKED;
    }
}
