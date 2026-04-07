package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response from POST /accounts/{id}/accessTokens.
 * Asaas returns the new API key in the "token" field (and sometimes "apiKey").
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasAccessTokenResponse {

    private String id;
    private String name;

    /**
     * The actual API key value — returned only at creation.
     * Asaas may use "token" or "apiKey" depending on API version.
     */
    private String token;
    private String apiKey;

    private Boolean enabled;
    private String expirationDate;
    private String dateCreated;

    /**
     * Returns the API key value regardless of which field Asaas populated.
     */
    public String resolveToken() {
        if (token != null && !token.isBlank()) return token;
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        return null;
    }
}

