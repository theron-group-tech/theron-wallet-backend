package com.theron.wallet.exception;

import com.theron.wallet.integration.AsaasErrorBodies;
import com.theron.wallet.integration.AsaasSecretRedactor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleFieldValidation(
            FieldValidationException ex, HttpServletRequest request) {
        log.warn("Field validation: {} — path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCodes.VALIDATION_ERROR,
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getFieldErrors()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: {} — path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(ApiErrorResponse.of(
                ex.getStatus().value(),
                ApiErrorCodes.of(ex),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(AsaasApiException.class)
    public ResponseEntity<ApiErrorResponse> handleAsaasApiException(AsaasApiException ex, HttpServletRequest request) {
        String asaasDescription = AsaasErrorBodies.extractFirstDescription(ex.getAsaasErrorBody());
        String message = asaasDescription != null
                ? "Asaas validation error: " + AsaasSecretRedactor.redact(asaasDescription)
                : "Payment provider error. Please try again later.";
        message = AsaasSecretRedactor.redact(message);

        log.error("Asaas API error: status={}, message={}, path={}",
                ex.getAsaasStatusCode(), message, request.getRequestURI());

        return ResponseEntity.status(ex.getStatus()).body(ApiErrorResponse.of(
                ex.getStatus().value(),
                ApiErrorCodes.ASAAS_ERROR,
                ex.getStatus().getReasonPhrase(),
                message,
                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(sensitiveField(fe.getField()) ? null : fe.getRejectedValue())
                        .build())
                .toList();

        log.warn("Validation failed: {} errors — path={}", fieldErrors.size(), request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCodes.VALIDATION_ERROR,
                "Validation Failed",
                "One or more fields have invalid values",
                request.getRequestURI(),
                fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body — path={}", request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCodes.VALIDATION_ERROR,
                "Bad Request",
                "Malformed JSON request body",
                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch: param={}, value={} — path={}", ex.getName(), ex.getValue(), request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ApiErrorCodes.VALIDATION_ERROR,
                "Bad Request",
                String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName()),
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                ApiErrorCodes.METHOD_NOT_ALLOWED,
                "Method Not Allowed",
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                ApiErrorCodes.NOT_FOUND,
                "Not Found",
                "The requested resource was not found",
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error — path={}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ApiErrorCodes.INTERNAL_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI()));
    }

    private static boolean sensitiveField(String field) {
        if (field == null) {
            return false;
        }
        String lower = field.toLowerCase();
        return lower.contains("password") || lower.contains("token") || lower.contains("secret")
                || lower.contains("apikey") || lower.contains("api-key");
    }
}
