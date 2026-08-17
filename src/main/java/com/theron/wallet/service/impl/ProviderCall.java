package com.theron.wallet.service.impl;

import com.theron.wallet.exception.AsaasApiException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

final class ProviderCall {

    private ProviderCall() {
    }

    static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResourceAccessException
                    || current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException
                    || current instanceof WebClientRequestException
                    || current instanceof ReadTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (current instanceof AsaasApiException asaas && asaas.getAsaasStatusCode() == 504) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isUniqueConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("ConstraintViolation") || name.contains("ConstraintViolationException")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    static String referenceOf(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= 100 ? description : description.substring(0, 100);
    }
}
