package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

public final class ApiErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String CONFLICT = "CONFLICT";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String ASAAS_ERROR = "ASAAS_ERROR";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ApiErrorCodes() {
    }

    public static String of(BusinessException exception) {
        if (exception instanceof UnauthorizedException) {
            return UNAUTHORIZED;
        }
        if (exception instanceof ForbiddenException) {
            return FORBIDDEN;
        }
        if (exception instanceof ResourceNotFoundException) {
            return NOT_FOUND;
        }
        if (exception instanceof DuplicateResourceException || exception instanceof InsufficientBalanceException) {
            return CONFLICT;
        }
        if (exception instanceof InvalidRequestException || exception instanceof SelfTransferException) {
            return INVALID_REQUEST;
        }
        if (exception instanceof SubaccountOperationBlockedException) {
            return FORBIDDEN;
        }
        if (exception instanceof AsaasApiException || exception instanceof AsaasErrorException) {
            return ASAAS_ERROR;
        }
        if (exception instanceof FieldValidationException) {
            return VALIDATION_ERROR;
        }
        if (exception instanceof RateLimitException) {
            return RATE_LIMITED;
        }
        return of(exception.getStatus());
    }

    public static String of(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> UNAUTHORIZED;
            case FORBIDDEN -> FORBIDDEN;
            case NOT_FOUND -> NOT_FOUND;
            case CONFLICT -> CONFLICT;
            case UNPROCESSABLE_ENTITY -> INVALID_REQUEST;
            case BAD_REQUEST -> VALIDATION_ERROR;
            case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
            case BAD_GATEWAY, GATEWAY_TIMEOUT, TOO_MANY_REQUESTS -> ASAAS_ERROR;
            default -> INTERNAL_ERROR;
        };
    }
}
