package com.theron.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a transfer is attempted between the same sender and receiver customer.
 * Results in HTTP 400 Bad Request.
 */
public class SelfTransferException extends BusinessException {

    public SelfTransferException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
