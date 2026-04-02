package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigResponse;
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
 * Asaas webhook configuration client.
 * This client uses PER-SUBACCOUNT API keys (not the root key)
 * to register webhooks under each subaccount's context.
 */
@Slf4j
@Component
public class AsaasWebhookClient {

    private final AsaasProperties asaasProperties;
    private final WebClient.Builder webClientBuilder;

    public AsaasWebhookClient(AsaasProperties asaasProperties, WebClient.Builder webClientBuilder) {
        this.asaasProperties = asaasProperties;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Creates a webhook configuration under a subaccount's context.
     * Uses the subaccount's own API key for authentication.
     *
     * @param subaccountApiKey the decrypted API key of the subaccount (never logged)
     * @param request          the webhook configuration
     */
    public AsaasWebhookConfigResponse createWebhook(String subaccountApiKey, AsaasWebhookConfigRequest request) {
        log.info("Creating webhook in Asaas for subaccount: url={}", request.getUrl());

        WebClient client = buildClientWithKey(subaccountApiKey);

        return client.post()
                .uri("/webhooks")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create webhook in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasWebhookConfigResponse.class)
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
