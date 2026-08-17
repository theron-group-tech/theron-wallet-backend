package com.theron.wallet.enums;

public enum LimitTransactionType {
    PIX,
    TRANSFER,
    WITHDRAWAL,
    PAYMENT;

    public TransactionType toCountedType() {
        return switch (this) {
            case PIX -> TransactionType.PIX;
            case TRANSFER -> TransactionType.TRANSFER_OUT;
            case WITHDRAWAL -> TransactionType.WITHDRAWAL;
            case PAYMENT -> TransactionType.PAYMENT;
        };
    }
}
