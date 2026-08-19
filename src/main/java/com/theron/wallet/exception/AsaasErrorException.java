package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public class AsaasErrorException extends BusinessException {

    public AsaasErrorException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
