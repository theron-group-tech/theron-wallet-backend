package com.theron.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AsaasApiException extends BusinessException {

    private final int asaasStatusCode;
    private final String asaasErrorBody;

    public AsaasApiException(String message, int asaasStatusCode, String asaasErrorBody) {
        super(message, mapToHttpStatus(asaasStatusCode));
        this.asaasStatusCode = asaasStatusCode;
        this.asaasErrorBody = asaasErrorBody;
    }

    private static HttpStatus mapToHttpStatus(int asaasStatus) {
        return switch (asaasStatus) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401, 403 -> HttpStatus.BAD_GATEWAY;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
