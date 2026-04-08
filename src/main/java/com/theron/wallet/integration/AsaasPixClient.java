package com.theron.wallet.integration;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.*;
import com.theron.wallet.exception.AsaasApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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
 * Asaas PIX client — gerenciamento de chaves Pix e QR Codes estáticos.
 * Todas as operações usam a API key da subconta para isolamento de tenant.
 */
@Slf4j
@Component
public class AsaasPixClient {

    private final AsaasProperties asaasProperties;
    private final WebClient.Builder webClientBuilder;

    public AsaasPixClient(AsaasProperties asaasProperties, WebClient.Builder webClientBuilder) {
        this.asaasProperties = asaasProperties;
        this.webClientBuilder = webClientBuilder;
    }

    public AsaasPixKeyResponse createPixKey(String apiKey, AsaasPixKeyRequest request) {
        log.info("Creating PIX key in Asaas: type={}", request.getType());

        return buildClientWithKey(apiKey).post()
                .uri("/pix/addressKeys")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create PIX key in Asaas",
                                        response.statusCode().value(), body))))
                .bodyToMono(AsaasPixKeyResponse.class)
                .block();
    }

    public AsaasListResponse<AsaasPixKeyResponse> listPixKeys(String apiKey) {
        log.info("Listing PIX keys in Asaas");

        return buildClientWithKey(apiKey).get()
                .uri("/pix/addressKeys")
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to list PIX keys in Asaas",
                                        response.statusCode().value(), body))))
                .bodyToMono(new ParameterizedTypeReference<AsaasListResponse<AsaasPixKeyResponse>>() {})
                .block();
    }

    public void deletePixKey(String apiKey, String pixKeyId) {
        log.info("Deleting PIX key in Asaas: pixKeyId={}", pixKeyId);

        buildClientWithKey(apiKey).delete()
                .uri("/pix/addressKeys/{id}", pixKeyId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to delete PIX key in Asaas",
                                        response.statusCode().value(), body))))
                .bodyToMono(Void.class)
                .block();
    }

    public AsaasPixStaticQrCodeResponse createStaticQrCode(
            String apiKey, String pixKeyId, AsaasPixStaticQrCodeRequest request) {
        log.info("Creating static PIX QR code in Asaas: pixKeyId={}", pixKeyId);

        return buildClientWithKey(apiKey).post()
                .uri("/pix/addressKeys/{id}/qrCodes/static", pixKeyId)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to create static PIX QR code in Asaas",
                                        response.statusCode().value(), body))))
                .bodyToMono(AsaasPixStaticQrCodeResponse.class)
                .block();
    }

    public void deleteStaticQrCode(String apiKey, String qrCodeId) {
        log.info("Deleting static PIX QR code in Asaas: qrCodeId={}", qrCodeId);

        buildClientWithKey(apiKey).delete()
                .uri("/pix/qrCodes/static/{id}", qrCodeId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new AsaasApiException(
                                        "Failed to delete static PIX QR code in Asaas",
                                        response.statusCode().value(), body))))
                .bodyToMono(Void.class)
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
