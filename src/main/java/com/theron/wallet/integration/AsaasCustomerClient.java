package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCreateCustomerResponse;
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
 * Creates and manages Asaas customers within a subaccount's context.
 * Each call uses the subaccount's own API key — never the platform root key.
 */
@Slf4j
@Component
public class AsaasCustomerClient {

    private final AsaasProperties asaasProperties;
    private final WebClient.Builder webClientBuilder;

    public AsaasCustomerClient(AsaasProperties asaasProperties, WebClient.Builder webClientBuilder) {
        this.asaasProperties = asaasProperties;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Creates a customer in the Asaas subaccount identified by {@code apiKey}.
     *
     * @param apiKey  the subaccount's decrypted Asaas API key
     * @param request customer data
     * @return the created customer with the Asaas-assigned ID
     */
    public AsaasCreateCustomerResponse createCustomer(String apiKey, AsaasCreateCustomerRequest request) {
        log.info("Creating Asaas customer: cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));

        return buildClientWithKey(apiKey).post()
                .uri("/customers")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create customer in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasCreateCustomerResponse.class)
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

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) return "****";
        return cpfCnpj.substring(0, cpfCnpj.length() - 4) + "****";
    }
}

