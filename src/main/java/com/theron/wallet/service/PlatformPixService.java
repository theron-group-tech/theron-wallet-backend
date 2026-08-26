package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;

import java.util.List;

public interface PlatformPixService {

    List<PlatformPixKeyResponse> listKeys();

    PlatformPixKeyResponse createKey(CreatePlatformPixKeyRequest request);

    void deleteKey(String asaasPixKeyId);
}
