package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OauthClientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One-shot response that includes the plaintext client secret.
 * The secret is never stored or returned again after create/rotate.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OauthClientSecretResponse {

    private UUID id;
    private UUID organizationId;
    private String clientId;
    private String name;
    private OauthClientStatus status;
    private OauthClientEnvironment environment;
    private List<String> scopes;
    /** Exactly one Account ID (OAuth client ↔ Account is 1:1). */
    private List<UUID> accountIds;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;

    @Schema(description = "Plaintext client secret — shown only once")
    private String clientSecret;
}
