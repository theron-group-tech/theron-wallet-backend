package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCustomerResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.exception.AsaasApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasCustomerClient {

    private final WebClient asaasWebClient;

    public AsaasCustomerResponse create(AsaasCustomerRequest request) {
        log.info("Creating customer in Asaas: cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));

        return asaasWebClient.post()
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
                .bodyToMono(AsaasCustomerResponse.class)
                .block();
    }

    public AsaasCustomerResponse retrieve(String asaasCustomerId) {
        log.info("Retrieving customer from Asaas: id={}", asaasCustomerId);

        return asaasWebClient.get()
                .uri("/customers/{id}", asaasCustomerId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to retrieve customer from Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasCustomerResponse.class)
                .block();
    }

    public AsaasListResponse<AsaasCustomerResponse> list(int offset, int limit) {
        log.info("Listing customers from Asaas: offset={}, limit={}", offset, limit);

        return asaasWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/customers")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to list customers from Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(new ParameterizedTypeReference<AsaasListResponse<AsaasCustomerResponse>>() {})
                .block();
    }

    public AsaasCustomerResponse update(String asaasCustomerId, AsaasCustomerRequest request) {
        log.info("Updating customer in Asaas: id={}", asaasCustomerId);

        return asaasWebClient.put()
                .uri("/customers/{id}", asaasCustomerId)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to update customer in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasCustomerResponse.class)
                .block();
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) {
            return "****";
        }
        return cpfCnpj.substring(0, cpfCnpj.length() - 4);
    }
}
