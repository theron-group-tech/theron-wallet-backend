package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.OauthClientEnvironment;
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
public class OauthClientResponse {

    private UUID id;
    private UUID organizationId;
    private String clientId;
    private String name;
    private OauthClientStatus status;
    private OauthClientEnvironment environment;
    private List<String> scopes;
    private List<UUID> accountIds;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;
}
