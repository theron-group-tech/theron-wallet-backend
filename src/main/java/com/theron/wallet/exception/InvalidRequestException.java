package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

