package com.theron.wallet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsaasPaymentEvent {
    PAYMENT_CREATED("PAYMENT_CREATED"),
    PAYMENT_UPDATED("PAYMENT_UPDATED"),
    PAYMENT_CONFIRMED("PAYMENT_CONFIRMED"),
    PAYMENT_RECEIVED("PAYMENT_RECEIVED"),
    PAYMENT_OVERDUE("PAYMENT_OVERDUE"),
    PAYMENT_DELETED("PAYMENT_DELETED"),
    PAYMENT_REFUNDED("PAYMENT_REFUNDED"),
    PAYMENT_CHARGEBACK_REQUESTED("PAYMENT_CHARGEBACK_REQUESTED"),
    PAYMENT_CHARGEBACK_DISPUTE("PAYMENT_CHARGEBACK_DISPUTE"),
    PAYMENT_AWAITING_CHARGEBACK_REVERSAL("PAYMENT_AWAITING_CHARGEBACK_REVERSAL");

    private final String value;

    public boolean isConfirmation() {
        return this == PAYMENT_CONFIRMED || this == PAYMENT_RECEIVED;
    }

    public boolean isCancellation() {
        return this == PAYMENT_OVERDUE || this == PAYMENT_DELETED;
    }

    public boolean isReversal() {
        return this == PAYMENT_REFUNDED || this == PAYMENT_CHARGEBACK_REQUESTED;
    }
}
