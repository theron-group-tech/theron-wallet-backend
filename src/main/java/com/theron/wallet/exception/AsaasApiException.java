package com.theron.wallet.exception;

import com.theron.wallet.integration.AsaasSecretRedactor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Duration;

@Getter
public class AsaasApiException extends BusinessException {

    private final int asaasStatusCode;
    private final String asaasErrorBody;
    private final Duration retryAfter;

    public AsaasApiException(String message, int asaasStatusCode, String asaasErrorBody) {
        this(message, asaasStatusCode, asaasErrorBody, null);
    }

    public AsaasApiException(String message, int asaasStatusCode, String asaasErrorBody, Duration retryAfter) {
        super(AsaasSecretRedactor.redact(message), mapToHttpStatus(asaasStatusCode));
        this.asaasStatusCode = asaasStatusCode;
        this.asaasErrorBody = AsaasSecretRedactor.redact(asaasErrorBody);
        this.retryAfter = retryAfter;
    }

    private static HttpStatus mapToHttpStatus(int asaasStatus) {
        return switch (asaasStatus) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401, 403 -> HttpStatus.BAD_GATEWAY;
            case 404 -> HttpStatus.NOT_FOUND;
            case 408, 504 -> HttpStatus.GATEWAY_TIMEOUT;
            case 409 -> HttpStatus.CONFLICT;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
