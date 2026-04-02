package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.asaas.AsaasPixQrCodeResponse;
import com.theron.wallet.exception.AsaasApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Asaas payment client with per-request API key support for tenant-aware operations.
 * Each method accepts an API key that determines the Asaas account context.
 */
@Slf4j
@Component
public class AsaasPaymentClient {

    private final AsaasProperties asaasProperties;
    private final WebClient.Builder webClientBuilder;

    public AsaasPaymentClient(AsaasProperties asaasProperties, WebClient.Builder webClientBuilder) {
        this.asaasProperties = asaasProperties;
        this.webClientBuilder = webClientBuilder;
    }

    public AsaasPaymentResponse createPayment(String apiKey, AsaasPaymentRequest request) {
        log.info("Creating PIX payment in Asaas: customer={}, value={}", request.getCustomer(), request.getValue());

        return buildClientWithKey(apiKey).post()
                .uri("/payments")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create payment in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasPaymentResponse.class)
                .block();
    }

    public AsaasPaymentResponse retrievePayment(String apiKey, String paymentId) {
        log.info("Retrieving payment from Asaas: id={}", paymentId);

        return buildClientWithKey(apiKey).get()
                .uri("/payments/{id}", paymentId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to retrieve payment from Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasPaymentResponse.class)
                .block();
    }

    public AsaasPixQrCodeResponse getPixQrCode(String apiKey, String paymentId) {
        log.info("Retrieving PIX QR code from Asaas: paymentId={}", paymentId);

        return buildClientWithKey(apiKey).get()
                .uri("/payments/{id}/pixQrCode", paymentId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to retrieve PIX QR code from Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasPixQrCodeResponse.class)
                .block();
    }

    private WebClient buildClientWithKey(String apiKey) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(asaasProperties.getTimeout().getRead()));

        return webClientBuilder
                .clone()
                .baseUrl(asaasProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("access_token", apiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
