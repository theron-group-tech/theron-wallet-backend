package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
