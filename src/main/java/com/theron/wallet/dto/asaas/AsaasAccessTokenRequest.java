package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for POST /accounts/{id}/accessTokens.
 * Generates a new API access token for an existing Asaas subaccount.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasAccessTokenRequest {

    /** Optional name for the token. Defaults to a system-generated name if not provided. */
    private String name;
}

