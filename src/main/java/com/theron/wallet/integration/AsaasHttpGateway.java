package com.theron.wallet.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.exception.AsaasApiException;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
public class AsaasHttpGateway {

    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(200);

    private final AsaasProperties asaasProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public AsaasHttpGateway(
            AsaasProperties asaasProperties, WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.asaasProperties = asaasProperties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, asaasProperties.getTimeout().getConnect())
                .responseTimeout(Duration.ofMillis(asaasProperties.getTimeout().getRead()));
        this.webClient = webClientBuilder.clone()
                .baseUrl(asaasProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    public <T> T get(String apiKey, String uri, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.GET, apiKey, uri, null, false, null, responseType, null, uriVariables);
    }

    public <T> T get(String apiKey, String uri, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.GET, apiKey, uri, null, false, null, null, responseType, uriVariables);
    }

    public <T> T post(String apiKey, String uri, Object body, Class<T> responseType, Object... uriVariables) {
        return execute(HttpMethod.POST, apiKey, uri, body, false, null, responseType, null, uriVariables);
    }

    public <T> T postFinancial(
            String apiKey, String uri, Object body, String idempotencyKey, Class<T> responseType) {
        return execute(HttpMethod.POST, apiKey, uri, body, true, idempotencyKey, responseType, null);
    }

    public void delete(String apiKey, String uri, Object... uriVariables) {
        execute(HttpMethod.DELETE, apiKey, uri, null, false, null, Void.class, null, uriVariables);
    }

    private <T> T execute(
            HttpMethod method,
            String apiKey,
            String uri,
            Object body,
            boolean financial,
            String idempotencyKey,
            Class<T> responseClass,
            ParameterizedTypeReference<T> responseRef,
            Object... uriVariables) {

        int maxAttempts = Math.max(1, asaasProperties.getRetry().getMaxAttempts());
        AsaasApiException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return exchangeOnce(
                        method, apiKey, uri, body, idempotencyKey, responseClass, responseRef, uriVariables);
            } catch (AsaasApiException ex) {
                lastError = ex;
                if (!shouldRetry(method, financial, ex, attempt, maxAttempts)) {
                    throw ex;
                }
                sleepBeforeRetry(ex, attempt);
            } catch (RuntimeException ex) {
                if (!isTimeout(ex)) {
                    throw unwrapAsaas(ex);
                }
                AsaasApiException timeout = new AsaasApiException("Asaas request timed out", 504, null);
                lastError = timeout;
                if (!shouldRetryTimeout(method, financial, attempt, maxAttempts)) {
                    throw timeout;
                }
                sleepBeforeRetry(timeout, attempt);
            }
        }
        throw lastError != null ? lastError : new AsaasApiException("Asaas request failed", 502, null);
    }

    private <T> T exchangeOnce(
            HttpMethod method,
            String apiKey,
            String uri,
            Object body,
            String idempotencyKey,
            Class<T> responseClass,
            ParameterizedTypeReference<T> responseRef,
            Object... uriVariables) {

        WebClient.RequestBodySpec spec = webClient.method(method)
                .uri(uri, uriVariables)
                .header("access_token", apiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            spec.header("Idempotency-Key", idempotencyKey);
        }

        WebClient.RequestHeadersSpec<?> headersSpec = body != null ? spec.bodyValue(body) : spec;
        try {
            return headersSpec.exchangeToMono(response -> {
                if (response.statusCode().isError()) {
                    Duration retryAfter = parseRetryAfter(response.headers().asHttpHeaders());
                    int status = response.statusCode().value();
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(errorBody -> Mono.error(new AsaasApiException(
                                    "Asaas API request failed", status, errorBody, retryAfter)));
                }
                if (shouldLogRawTransferBody(method, uri)) {
                    return readAndLogTransferBody(response, uri, responseClass, responseRef);
                }
                if (responseRef != null) {
                    return response.bodyToMono(responseRef);
                }
                if (isVoid(responseClass)) {
                    return response.releaseBody().then(Mono.empty());
                }
                return response.bodyToMono(responseClass);
            }).block();
        } catch (AsaasApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (isTimeout(ex)) {
                throw ex;
            }
            throw unwrapAsaas(ex);
        }
    }

    private static boolean shouldLogRawTransferBody(HttpMethod method, String uri) {
        if (method != HttpMethod.POST || uri == null) {
            return false;
        }
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        return "/transfers".equals(path);
    }

    private <T> Mono<T> readAndLogTransferBody(
            ClientResponse response,
            String uri,
            Class<T> responseClass,
            ParameterizedTypeReference<T> responseRef) {
        int status = response.statusCode().value();
        String endpoint = "POST " + (uri != null ? uri : "/transfers");
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(rawBody -> {
                    log.info("Asaas response status={} endpoint={} body={}",
                            status, endpoint, AsaasSecretRedactor.redact(rawBody));
                    try {
                        if (responseRef != null) {
                            T parsed = objectMapper.readValue(
                                    rawBody, objectMapper.constructType(responseRef.getType()));
                            return Mono.just(parsed);
                        }
                        if (isVoid(responseClass)) {
                            return Mono.empty();
                        }
                        T parsed = objectMapper.readValue(rawBody, responseClass);
                        return Mono.just(parsed);
                    } catch (JsonProcessingException ex) {
                        return Mono.error(new AsaasApiException(
                                "Failed to parse Asaas transfer response", status, rawBody));
                    }
                });
    }

    private boolean shouldRetry(
            HttpMethod method, boolean financial, AsaasApiException ex, int attempt, int maxAttempts) {
        if (attempt >= maxAttempts) {
            return false;
        }
        int status = ex.getAsaasStatusCode();
        if (status == 429) {
            return true;
        }
        if (financial || method == HttpMethod.POST || method == HttpMethod.DELETE || method == HttpMethod.PUT) {
            return false;
        }
        return status >= 500;
    }

    private boolean shouldRetryTimeout(HttpMethod method, boolean financial, int attempt, int maxAttempts) {
        if (attempt >= maxAttempts || financial) {
            return false;
        }
        return method == HttpMethod.GET;
    }

    private void sleepBeforeRetry(AsaasApiException ex, int attempt) {
        Duration wait = Optional.ofNullable(ex.getRetryAfter())
                .filter(duration -> !duration.isNegative())
                .orElse(DEFAULT_BACKOFF.multipliedBy(1L << Math.min(attempt - 1, 4)));
        if (wait.isZero()) {
            return;
        }
        try {
            Thread.sleep(Math.min(wait.toMillis(), 5_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AsaasApiException("Asaas request interrupted", 504, null);
        }
    }

    private static Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration wait = Duration.between(ZonedDateTime.now(when.getZone()), when);
                return wait.isNegative() ? Duration.ZERO : wait;
            } catch (Exception ignoredDate) {
                return null;
            }
        }
    }

    private static boolean isVoid(Class<?> responseClass) {
        return responseClass == null || responseClass == Void.class || responseClass == void.class;
    }

    private static RuntimeException unwrapAsaas(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof AsaasApiException asaas) {
                return asaas;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return ex;
    }

    static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientRequestException
                    || current instanceof ReadTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof java.util.concurrent.TimeoutException) {
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

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("Asaas request {} {}", request.method(), safeUrl(request));
            return Mono.just(request);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.info("Asaas response status={}", response.statusCode());
            return Mono.just(response);
        });
    }

    private static String safeUrl(ClientRequest request) {
        return AsaasSecretRedactor.redact(String.valueOf(request.url()));
    }
}
