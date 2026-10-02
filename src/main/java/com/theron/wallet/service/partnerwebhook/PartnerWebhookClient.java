package com.theron.wallet.service.partnerwebhook;

import com.theron.wallet.config.VemComigoSplitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerWebhookClient {

    private final WebClient.Builder webClientBuilder;
    private final VemComigoSplitProperties vemComigoSplitProperties;

    public void post(String deliveryId, String eventType, String payloadJson) {
        String url = vemComigoSplitProperties.getWebhookUrl();
        String secret = vemComigoSplitProperties.getWebhookSecret();
        String signature = PartnerWebhookSigner.sign(secret, payloadJson);
        String timestamp = Instant.now().toString();

        webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Theron-Signature", signature)
                .header("X-Theron-Event", eventType)
                .header("X-Theron-Delivery-Id", deliveryId)
                .header("X-Theron-Timestamp", timestamp)
                .bodyValue(payloadJson)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    public static String describeFailure(Throwable ex) {
        if (ex instanceof WebClientResponseException wcre) {
            return "HTTP " + wcre.getStatusCode().value() + ": " + truncate(wcre.getResponseBodyAsString());
        }
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return truncate(root.getClass().getSimpleName() + ": " + root.getMessage());
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
