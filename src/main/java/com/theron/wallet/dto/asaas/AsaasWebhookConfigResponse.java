package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasWebhookConfigResponse {

    private String id;
    private String name;
    private String url;
    private String email;
    private Boolean enabled;
    private Boolean interrupted;
    private String apiVersion;
    private String authToken;
    private String sendType;
    private List<String> events;
}
