package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.OauthTokenResponse;
import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.entity.OauthClientAudit;
import com.theron.wallet.entity.OauthClientScope;
import com.theron.wallet.enums.OauthClientStatus;
import com.theron.wallet.exception.OauthTokenException;
import com.theron.wallet.repository.OauthClientAuditRepository;
import com.theron.wallet.repository.OauthClientRepository;
import com.theron.wallet.security.JwtTokenProvider;
import com.theron.wallet.security.OauthClientSecretHasher;
import com.theron.wallet.service.OauthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OauthTokenServiceImpl implements OauthTokenService {

    private final OauthClientRepository oauthClientRepository;
    private final OauthClientAuditRepository oauthClientAuditRepository;
    private final OauthClientSecretHasher secretHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final OauthClientLoader oauthClientLoader;

    @Override
    @Transactional
    public OauthTokenResponse issueClientCredentialsToken(String clientId, String clientSecret, String grantType) {
        if (grantType == null || grantType.isBlank()) {
            throw OauthTokenException.invalidRequest("grant_type is required");
        }
        if (!"client_credentials".equals(grantType)) {
            throw OauthTokenException.unsupportedGrantType();
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw OauthTokenException.invalidClient("Invalid client credentials");
        }

        OauthClient client = oauthClientLoader.loadWithDetailsByClientId(clientId.trim()).orElse(null);
        if (client == null
                || client.getStatus() != OauthClientStatus.ACTIVE
                || !secretHasher.matches(clientSecret, client.getClientSecretHash())) {
            throw OauthTokenException.invalidClient("Invalid client credentials");
        }

        var scopes = client.getScopes().stream()
                .map(OauthClientScope::getScope)
                .sorted()
                .toList();

        String accessToken = jwtTokenProvider.generateClientAccessToken(
                client.getId(),
                client.getOrganization().getId(),
                scopes);

        client.setLastUsedAt(LocalDateTime.now());
        oauthClientRepository.save(client);
        oauthClientAuditRepository.save(OauthClientAudit.builder()
                .client(client)
                .action("TOKEN_ISSUED")
                .detail("scopes=" + String.join(" ", scopes))
                .build());

        return OauthTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getOauthAccessExpirationMs() / 1000)
                .scope(scopes.stream().collect(Collectors.joining(" ")))
                .build();
    }
}
