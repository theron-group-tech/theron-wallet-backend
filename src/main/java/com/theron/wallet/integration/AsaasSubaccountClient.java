package com.theron.wallet.integration;

import com.theron.wallet.dto.asaas.AsaasSubaccountRequest;
import com.theron.wallet.dto.asaas.AsaasSubaccountResponse;
import com.theron.wallet.exception.AsaasApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Asaas subaccount (conta-filha) management.
 * Uses the root account WebClient since subaccount creation requires the parent key.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsaasSubaccountClient {

    private final WebClient asaasWebClient;

    public AsaasSubaccountResponse createSubaccount(AsaasSubaccountRequest request) {
        log.info("Creating subaccount in Asaas: cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));

        return asaasWebClient.post()
                .uri("/accounts")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create subaccount in Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasSubaccountResponse.class)
                .block();
    }

    public AsaasSubaccountResponse retrieveSubaccount(String asaasAccountId) {
        log.info("Retrieving subaccount from Asaas: id={}", asaasAccountId);

        return asaasWebClient.get()
                .uri("/accounts/{id}", asaasAccountId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to retrieve subaccount from Asaas",
                                        response.statusCode().value(),
                                        body
                                )))
                )
                .bodyToMono(AsaasSubaccountResponse.class)
                .block();
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) {
            return "****";
        }
        return cpfCnpj.substring(0, cpfCnpj.length() - 4) + "****";
    }
}
