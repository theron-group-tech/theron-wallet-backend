package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.request.UpdateOauthClientAccountsRequest;
import com.theron.wallet.dto.request.UpdateOauthClientScopesRequest;
import com.theron.wallet.dto.response.OauthClientResponse;
import com.theron.wallet.dto.response.OauthClientSecretResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.entity.OauthClientAccount;
import com.theron.wallet.entity.OauthClientAudit;
import com.theron.wallet.entity.OauthClientScope;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OauthClientStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OauthClientAuditRepository;
import com.theron.wallet.repository.OauthClientRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.security.OauthClientSecretHasher;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.OauthClientAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OauthClientAdminServiceImpl implements OauthClientAdminService {

    private static final Set<String> ALLOWED_B2B_SCOPES = Set.of(
            PermissionCodes.ORGANIZATION_READ,
            PermissionCodes.WALLET_READ,
            PermissionCodes.WALLET_TRANSFER,
            PermissionCodes.TRANSACTIONS_READ,
            PermissionCodes.TRANSACTIONS_CREATE,
            PermissionCodes.PIX_READ,
            PermissionCodes.PIX_CREATE,
            PermissionCodes.PIX_TRANSFER,
            PermissionCodes.BENEFICIARIES_READ,
            PermissionCodes.BENEFICIARIES_CREATE,
            PermissionCodes.BENEFICIARIES_UPDATE,
            PermissionCodes.BENEFICIARIES_DELETE
    );

    private final OauthClientRepository oauthClientRepository;
    private final OauthClientAuditRepository oauthClientAuditRepository;
    private final OrganizationRepository organizationRepository;
    private final AccountRepository accountRepository;
    private final OauthClientSecretHasher secretHasher;
    private final OauthClientLoader oauthClientLoader;

    @Override
    @Transactional
    public OauthClientSecretResponse create(UUID organizationId, CreateOauthClientRequest request, UUID adminId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));

        List<String> scopes = normalizeScopes(request.getScopes());
        validateScopes(scopes);
        List<Account> accounts = resolveAccountsForOrganization(organizationId, request.getAccountIds());

        String publicClientId = secretHasher.generatePublicClientId();
        String plainSecret = secretHasher.generatePlainSecret();

        OauthClientEnvironment environment = request.getEnvironment() != null
                ? request.getEnvironment()
                : OauthClientEnvironment.SANDBOX;

        OauthClient client = OauthClient.builder()
                .organization(organization)
                .clientId(publicClientId)
                .clientSecretHash(secretHasher.sha256Hex(plainSecret))
                .name(request.getName().trim())
                .status(OauthClientStatus.ACTIVE)
                .environment(environment)
                .scopes(new HashSet<>())
                .accounts(new HashSet<>())
                .build();

        applyScopes(client, scopes);
        applyAccounts(client, accounts);

        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "CREATE", adminId, "scopes=" + String.join(" ", scopes)
                + "; accounts=" + accounts.size());

        return toSecretResponse(saved, plainSecret);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OauthClientResponse> list(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        return oauthClientRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OauthClientSecretResponse rotateSecret(UUID organizationId, UUID clientId, UUID adminId) {
        OauthClient client = requireClient(organizationId, clientId);
        if (client.getStatus() == OauthClientStatus.REVOKED) {
            throw new InvalidRequestException("Cannot rotate secret of a revoked OAuth client");
        }

        String plainSecret = secretHasher.generatePlainSecret();
        client.setClientSecretHash(secretHasher.sha256Hex(plainSecret));
        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "ROTATE_SECRET", adminId, null);
        return toSecretResponse(saved, plainSecret);
    }

    @Override
    @Transactional
    public OauthClientResponse revoke(UUID organizationId, UUID clientId, UUID adminId) {
        OauthClient client = requireClient(organizationId, clientId);
        if (client.getStatus() == OauthClientStatus.REVOKED) {
            return toResponse(client);
        }

        client.setStatus(OauthClientStatus.REVOKED);
        client.setRevokedAt(LocalDateTime.now());
        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "REVOKE", adminId, null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OauthClientResponse replaceScopes(
            UUID organizationId, UUID clientId, UpdateOauthClientScopesRequest request, UUID adminId) {
        OauthClient client = requireClient(organizationId, clientId);
        if (client.getStatus() == OauthClientStatus.REVOKED) {
            throw new InvalidRequestException("Cannot update scopes of a revoked OAuth client");
        }

        List<String> scopes = normalizeScopes(request.getScopes());
        validateScopes(scopes);
        client.getScopes().clear();
        applyScopes(client, scopes);

        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "UPDATE_SCOPES", adminId, "scopes=" + String.join(" ", scopes));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OauthClientResponse replaceAccounts(
            UUID organizationId, UUID clientId, UpdateOauthClientAccountsRequest request, UUID adminId) {
        OauthClient client = requireClient(organizationId, clientId);
        if (client.getStatus() == OauthClientStatus.REVOKED) {
            throw new InvalidRequestException("Cannot update accounts of a revoked OAuth client");
        }

        List<Account> accounts = resolveAccountsForOrganization(organizationId, request.getAccountIds());
        client.getAccounts().clear();
        applyAccounts(client, accounts);

        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "UPDATE_ACCOUNTS", adminId, "accounts=" + accounts.size());
        return toResponse(saved);
    }

    private OauthClient requireClient(UUID organizationId, UUID clientId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        OauthClient client = oauthClientLoader.loadWithDetailsById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("OauthClient", "id", clientId));
        if (!client.getOrganization().getId().equals(organizationId)) {
            throw new ResourceNotFoundException("OauthClient", "id", clientId);
        }
        return client;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        return scopes.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private void validateScopes(List<String> scopes) {
        if (scopes.isEmpty()) {
            throw new InvalidRequestException("At least one scope is required");
        }
        List<String> unknown = scopes.stream()
                .filter(scope -> !ALLOWED_B2B_SCOPES.contains(scope))
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidRequestException("Unknown or disallowed OAuth scopes: " + String.join(", ", unknown));
        }
    }

    private List<Account> resolveAccountsForOrganization(UUID organizationId, List<UUID> accountIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(accountIds);
        List<Account> accounts = accountRepository.findAllById(uniqueIds);
        if (accounts.size() != uniqueIds.size()) {
            Set<UUID> found = accounts.stream().map(Account::getId).collect(Collectors.toSet());
            UUID missing = uniqueIds.stream().filter(id -> !found.contains(id)).findFirst().orElse(null);
            throw new ResourceNotFoundException("Account", "id", missing);
        }
        for (Account account : accounts) {
            if (account.getOrganization() == null || !organizationId.equals(account.getOrganization().getId())) {
                throw new InvalidRequestException(
                        "Account " + account.getId() + " does not belong to organization " + organizationId);
            }
        }
        return accounts;
    }

    private void applyScopes(OauthClient client, List<String> scopes) {
        for (String scope : scopes) {
            client.getScopes().add(OauthClientScope.builder()
                    .client(client)
                    .scope(scope)
                    .build());
        }
    }

    private void applyAccounts(OauthClient client, List<Account> accounts) {
        for (Account account : accounts) {
            client.getAccounts().add(OauthClientAccount.builder()
                    .client(client)
                    .account(account)
                    .build());
        }
    }

    private void audit(OauthClient client, String action, UUID adminId, String detail) {
        oauthClientAuditRepository.save(OauthClientAudit.builder()
                .client(client)
                .action(action)
                .actorAdminId(adminId)
                .detail(detail)
                .build());
    }

    private OauthClientResponse toResponse(OauthClient client) {
        return OauthClientResponse.builder()
                .id(client.getId())
                .organizationId(client.getOrganization().getId())
                .clientId(client.getClientId())
                .name(client.getName())
                .status(client.getStatus())
                .environment(client.getEnvironment())
                .scopes(client.getScopes().stream().map(OauthClientScope::getScope).sorted().toList())
                .accountIds(client.getAccounts().stream()
                        .map(a -> a.getAccount().getId())
                        .sorted()
                        .toList())
                .createdAt(client.getCreatedAt())
                .revokedAt(client.getRevokedAt())
                .lastUsedAt(client.getLastUsedAt())
                .build();
    }

    private OauthClientSecretResponse toSecretResponse(OauthClient client, String plainSecret) {
        return OauthClientSecretResponse.builder()
                .id(client.getId())
                .organizationId(client.getOrganization().getId())
                .clientId(client.getClientId())
                .name(client.getName())
                .status(client.getStatus())
                .environment(client.getEnvironment())
                .scopes(client.getScopes().stream().map(OauthClientScope::getScope).sorted().toList())
                .accountIds(client.getAccounts().stream()
                        .map(a -> a.getAccount().getId())
                        .sorted()
                        .toList())
                .createdAt(client.getCreatedAt())
                .revokedAt(client.getRevokedAt())
                .lastUsedAt(client.getLastUsedAt())
                .clientSecret(plainSecret)
                .build();
    }
}
