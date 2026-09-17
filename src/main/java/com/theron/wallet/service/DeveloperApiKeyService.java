package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateTheronApiKeyRequest;
import com.theron.wallet.dto.response.TheronApiKeyResponse;

import java.util.List;
import java.util.UUID;

public interface DeveloperApiKeyService {

    TheronApiKeyResponse create(UUID actorUserId, CreateTheronApiKeyRequest request);

    List<TheronApiKeyResponse> list(UUID actorUserId);

    void revoke(UUID actorUserId, UUID apiKeyId);

    TheronApiKeyResponse rotate(UUID actorUserId, UUID apiKeyId);
}
