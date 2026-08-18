package com.theron.wallet.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    public static final String TRACE_MDC_KEY = "correlationId";

    private final LocalDateTime timestamp;
    private final int status;
    private final String code;
    private final String error;
    private final String message;
    private final String path;
    private final String traceId;
    private final List<FieldError> fieldErrors;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }

    public static ApiErrorResponse of(
            int status, String code, String error, String message, String path) {
        return of(status, code, error, message, path, null);
    }

    public static ApiErrorResponse of(
            int status, String code, String error, String message, String path, List<FieldError> fieldErrors) {
        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .code(code)
                .error(error)
                .message(message)
                .path(path)
                .traceId(MDC.get(TRACE_MDC_KEY))
                .fieldErrors(fieldErrors)
                .build();
    }
}
