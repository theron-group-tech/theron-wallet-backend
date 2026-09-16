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

    /**
     * Load scopes + accounts on the same persistence-context instance.
     * Must not replace {@code scopes}/{@code accounts} Set references — both use
     * {@code orphanRemoval=true}; replacing them and later {@code save()} causes
     * Hibernate "collection with orphan deletion was no longer referenced".
     */
    private OauthClient hydrate(UUID id) {
        OauthClient client = oauthClientRepository.findByIdWithOrganization(id).orElseThrow();
        oauthClientRepository.findByIdWithScopes(id).orElseThrow();
        oauthClientRepository.findByIdWithAccounts(id).orElseThrow();
        client.getScopes().size();
        client.getAccounts().size();
        return client;
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
