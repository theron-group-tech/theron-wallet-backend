package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasWebhookConfigRequest {

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
