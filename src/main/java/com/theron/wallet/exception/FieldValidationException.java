package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class FieldValidationException extends BusinessException {

    private final List<ApiErrorResponse.FieldError> fieldErrors;

    public FieldValidationException(String message, List<ApiErrorResponse.FieldError> fieldErrors) {
        super(message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = fieldErrors;
    }

    public List<ApiErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
