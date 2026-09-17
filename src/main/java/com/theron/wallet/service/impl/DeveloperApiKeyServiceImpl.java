package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateTheronApiKeyRequest;
import com.theron.wallet.dto.response.TheronApiKeyResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.entity.OauthClientAccount;
import com.theron.wallet.entity.OauthClientAudit;
import com.theron.wallet.entity.OauthClientScope;
import com.theron.wallet.enums.OauthClientEnvironment;
import com.theron.wallet.enums.OauthClientStatus;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.OauthClientAuditRepository;
import com.theron.wallet.repository.OauthClientRepository;
import com.theron.wallet.security.OauthClientSecretHasher;
import com.theron.wallet.security.OrganizationContextResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.security.ResourceAuthorization;
import com.theron.wallet.service.DeveloperApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeveloperApiKeyServiceImpl implements DeveloperApiKeyService {

    private static final List<String> DEFAULT_SCOPES = List.of(
            PermissionCodes.ORGANIZATION_READ,
            PermissionCodes.WALLET_READ,
            PermissionCodes.TRANSACTIONS_READ,
            PermissionCodes.PIX_READ,
            PermissionCodes.PIX_CREATE,
            PermissionCodes.PIX_TRANSFER,
            PermissionCodes.CHARGES_READ,
            PermissionCodes.CHARGES_CREATE,
            PermissionCodes.CHARGES_CANCEL,
            PermissionCodes.ANTICIPATIONS_READ,
            PermissionCodes.ANTICIPATIONS_CREATE,
            PermissionCodes.ONBOARDING_READ,
            PermissionCodes.ONBOARDING_SUBMIT
    );

    private static final String SECRET_WARNING =
            "Store this credential securely. It will not be displayed again.";

    private final OrganizationContextResolver organizationContextResolver;
    private final ResourceAuthorization resourceAuthorization;
    private final AccountRepository accountRepository;
    private final OauthClientRepository oauthClientRepository;
    private final OauthClientAuditRepository oauthClientAuditRepository;
    private final OauthClientSecretHasher secretHasher;
    private final OauthClientLoader oauthClientLoader;

    @Override
    @Transactional
    public TheronApiKeyResponse create(UUID actorUserId, CreateTheronApiKeyRequest request) {
        UUID organizationId = requireOwnerOrg(actorUserId);
        Account account = requireMainAccount(organizationId, actorUserId);
        assertAccountAvailable(account.getId(), null);

        boolean live = false; // product default sandbox; live when environment dictates later
        String plainApiKey = secretHasher.generateTheronApiKey(live);
        String prefix = secretHasher.apiKeyPrefix(plainApiKey);

        OauthClient client = OauthClient.builder()
                .organization(account.getOrganization())
                .clientId(secretHasher.generatePublicClientId())
                .clientSecretHash(secretHasher.sha256Hex(plainApiKey))
                .apiKeyPrefix(prefix)
                .createdByUserId(actorUserId)
                .name(request.getName().trim())
                .status(OauthClientStatus.ACTIVE)
                .environment(live ? OauthClientEnvironment.PRODUCTION : OauthClientEnvironment.SANDBOX)
                .scopes(new HashSet<>())
                .accounts(new HashSet<>())
                .build();

        for (String scope : DEFAULT_SCOPES) {
            client.getScopes().add(OauthClientScope.builder().client(client).scope(scope).build());
        }
        client.getAccounts().add(OauthClientAccount.builder().client(client).account(account).build());

        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "CREATE_API_KEY", actorUserId, "account=" + account.getId() + "; prefix=" + prefix);

        return toSecretResponse(saved, account.getId(), plainApiKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TheronApiKeyResponse> list(UUID actorUserId) {
        UUID organizationId = requireOwnerOrg(actorUserId);
        return oauthClientRepository.findByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(c -> {
                    OauthClient hydrated = oauthClientLoader.loadWithDetailsById(c.getId()).orElse(c);
                    UUID accountId = OauthClientLoader.accountIds(hydrated).stream().findFirst().orElse(null);
                    return toMetaResponse(hydrated, accountId);
                })
                .toList();
    }

    @Override
    @Transactional
    public void revoke(UUID actorUserId, UUID apiKeyId) {
        UUID organizationId = requireOwnerOrg(actorUserId);
        OauthClient client = oauthClientRepository.findByIdAndOrganization_Id(apiKeyId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", "id", apiKeyId));
        if (client.getStatus() == OauthClientStatus.REVOKED) {
            return;
        }
        client.setStatus(OauthClientStatus.REVOKED);
        client.setRevokedAt(LocalDateTime.now());
        oauthClientRepository.save(client);
        audit(client, "REVOKE_API_KEY", actorUserId, "prefix=" + client.getApiKeyPrefix());
    }

    @Override
    @Transactional
    public TheronApiKeyResponse rotate(UUID actorUserId, UUID apiKeyId) {
        UUID organizationId = requireOwnerOrg(actorUserId);
        OauthClient client = oauthClientLoader.loadWithDetailsById(apiKeyId)
                .filter(c -> organizationId.equals(c.getOrganization().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", "id", apiKeyId));
        if (client.getStatus() != OauthClientStatus.ACTIVE) {
            throw new InvalidRequestException("Cannot rotate a non-active API key");
        }

        boolean live = client.getEnvironment() == OauthClientEnvironment.PRODUCTION;
        String plainApiKey = secretHasher.generateTheronApiKey(live);
        String prefix = secretHasher.apiKeyPrefix(plainApiKey);
        client.setClientSecretHash(secretHasher.sha256Hex(plainApiKey));
        client.setApiKeyPrefix(prefix);
        OauthClient saved = oauthClientRepository.save(client);
        audit(saved, "ROTATE_API_KEY", actorUserId, "prefix=" + prefix);

        UUID accountId = OauthClientLoader.accountIds(saved).stream().findFirst().orElse(null);
        return toSecretResponse(saved, accountId, plainApiKey);
    }

    private UUID requireOwnerOrg(UUID actorUserId) {
        UUID organizationId = organizationContextResolver.requireSingleOrganizationId(actorUserId);
        resourceAuthorization.requireOrganization(
                actorUserId, organizationId, PermissionCodes.DEVELOPER_API_KEYS_MANAGE);
        organizationContextResolver.requireOwner(organizationId, actorUserId);
        return organizationId;
    }

    private Account requireMainAccount(UUID organizationId, UUID ownerUserId) {
        return accountRepository.findByOrganization_IdAndOwnerUser_Id(organizationId, ownerUserId)
                .orElseThrow(() -> new ForbiddenException("Owner account not found for organization"));
    }

    private void assertAccountAvailable(UUID accountId, UUID excludeClientId) {
        if (oauthClientRepository.existsByAccountIdExcludingClient(accountId, excludeClientId)) {
            throw new InvalidRequestException(
                    "Account already has an API key / OAuth client (1:1 binding required). Rotate or revoke the existing key.");
        }
    }

    private void audit(OauthClient client, String action, UUID userId, String detail) {
        oauthClientAuditRepository.save(OauthClientAudit.builder()
                .client(client)
                .action(action)
                .actorAdminId(userId)
                .detail(detail)
                .build());
    }

    private TheronApiKeyResponse toMetaResponse(OauthClient client, UUID accountId) {
        return TheronApiKeyResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .prefix(client.getApiKeyPrefix())
                .status(client.getStatus())
                .organizationId(client.getOrganization().getId())
                .accountId(accountId)
                .scopes(OauthClientLoader.scopeCodes(client).stream().sorted().toList())
                .createdAt(client.getCreatedAt())
                .lastUsedAt(client.getLastUsedAt())
                .revokedAt(client.getRevokedAt())
                .build();
    }

    private TheronApiKeyResponse toSecretResponse(OauthClient client, UUID accountId, String plainApiKey) {
        TheronApiKeyResponse response = toMetaResponse(client, accountId);
        response.setApiKey(plainApiKey);
        response.setWarning(SECRET_WARNING);
        return response;
    }
}
