package com.theron.wallet.service.impl;

import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.entity.OauthClientAccount;
import com.theron.wallet.entity.OauthClientScope;
import com.theron.wallet.enums.OauthClientStatus;
import com.theron.wallet.repository.OauthClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OauthClientLoader {

    private final OauthClientRepository oauthClientRepository;

    @Transactional(readOnly = true)
    public Optional<OauthClient> loadActiveWithDetailsById(UUID id) {
        Optional<OauthClient> base = oauthClientRepository.findByIdWithOrganization(id);
        if (base.isEmpty() || base.get().getStatus() != OauthClientStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(hydrate(base.get().getId()));
    }

    @Transactional(readOnly = true)
    public Optional<OauthClient> loadWithDetailsById(UUID id) {
        if (oauthClientRepository.findByIdWithOrganization(id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(hydrate(id));
    }

    @Transactional(readOnly = true)
    public Optional<OauthClient> loadWithDetailsByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        Optional<OauthClient> base = oauthClientRepository.findByClientIdWithOrganization(clientId);
        return base.map(c -> hydrate(c.getId()));
    }

    private OauthClient hydrate(UUID id) {
        OauthClient withScopes = oauthClientRepository.findByIdWithScopes(id).orElseThrow();
        OauthClient withAccounts = oauthClientRepository.findByIdWithAccounts(id).orElseThrow();
        Set<OauthClientScope> scopes = new HashSet<>(withScopes.getScopes());
        Set<OauthClientAccount> accounts = new HashSet<>(withAccounts.getAccounts());
        withScopes.setScopes(scopes);
        withScopes.setAccounts(accounts);
        return withScopes;
    }

    public static Set<String> scopeCodes(OauthClient client) {
        return client.getScopes().stream()
                .map(OauthClientScope::getScope)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public static Set<UUID> accountIds(OauthClient client) {
        return client.getAccounts().stream()
                .map(OauthClientAccount::getAccount)
                .filter(a -> a != null)
                .map(a -> a.getId())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
