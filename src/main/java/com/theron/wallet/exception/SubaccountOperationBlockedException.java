package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class SubaccountOperationBlockedException extends BusinessException {

    public SubaccountOperationBlockedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
