package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasTransferRequest;
import com.theron.wallet.dto.asaas.AsaasTransferResponse;
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

@Slf4j
@Component
public class AsaasTransferClient {

    private final AsaasProperties asaasProperties;
    private final WebClient.Builder webClientBuilder;

    public AsaasTransferClient(AsaasProperties asaasProperties, WebClient.Builder webClientBuilder) {
        this.asaasProperties = asaasProperties;
        this.webClientBuilder = webClientBuilder;
    }

    public AsaasTransferResponse createTransfer(String apiKey, AsaasTransferRequest request) {
        log.info("Creating transfer in Asaas: value={}, pixKeyType={}", request.getValue(), request.getPixAddressKeyType());

        return buildClientWithKey(apiKey).post()
                .uri("/transfers")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create transfer in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasTransferResponse.class)
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
