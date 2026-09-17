package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.OauthClientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TheronApiKeyResponse {

    private UUID id;
    private String name;
    private String prefix;
    private OauthClientStatus status;
    private UUID organizationId;
    private UUID accountId;
    private List<String> scopes;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    /** Plaintext Theron API Key — only on create/rotate. Never on GET. */
    private String apiKey;

    private String warning;
}
