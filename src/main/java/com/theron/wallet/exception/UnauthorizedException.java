package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
