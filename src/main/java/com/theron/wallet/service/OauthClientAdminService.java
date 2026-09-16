package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateOauthClientRequest;
import com.theron.wallet.dto.request.UpdateOauthClientAccountsRequest;
import com.theron.wallet.dto.request.UpdateOauthClientScopesRequest;
import com.theron.wallet.dto.response.OauthClientResponse;
import com.theron.wallet.dto.response.OauthClientSecretResponse;

import java.util.List;
import java.util.UUID;

public interface OauthClientAdminService {

    OauthClientSecretResponse create(UUID organizationId, CreateOauthClientRequest request, UUID adminId);

    List<OauthClientResponse> list(UUID organizationId);

    OauthClientSecretResponse rotateSecret(UUID organizationId, UUID clientId, UUID adminId);

    OauthClientResponse revoke(UUID organizationId, UUID clientId, UUID adminId);

    OauthClientResponse replaceScopes(UUID organizationId, UUID clientId, UpdateOauthClientScopesRequest request, UUID adminId);

    OauthClientResponse replaceAccounts(UUID organizationId, UUID clientId, UpdateOauthClientAccountsRequest request, UUID adminId);
}
