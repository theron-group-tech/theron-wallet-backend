package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends BusinessException {

    public RateLimitException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
