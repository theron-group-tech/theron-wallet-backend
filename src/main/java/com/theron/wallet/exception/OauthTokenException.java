package com.theron.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * OAuth 2.0 token endpoint errors (RFC 6749) — not {@link ApiErrorResponse}.
 */
@Getter
public class OauthTokenException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OauthTokenException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OauthTokenException invalidClient(String description) {
        return new OauthTokenException("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }

    public static OauthTokenException invalidRequest(String description) {
        return new OauthTokenException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    public static OauthTokenException unsupportedGrantType() {
        return new OauthTokenException(
                "unsupported_grant_type",
                "Only client_credentials is supported",
                HttpStatus.BAD_REQUEST);
    }
}
