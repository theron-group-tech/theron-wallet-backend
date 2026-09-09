package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * RFC 6749 error body for {@code POST /oauth/token} only.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OauthErrorResponse {

    private final String error;

    @JsonProperty("error_description")
    private final String errorDescription;
}
