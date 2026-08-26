package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.request.CreatePlatformPixTransferRequest;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.dto.response.PlatformPixTransferResponse;
import com.theron.wallet.enums.PixKeyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PlatformPixService {

    List<PlatformPixKeyResponse> listKeys();

    PlatformPixKeyResponse createKey(CreatePlatformPixKeyRequest request);

    void deleteKey(String asaasPixKeyId);

    PixKeyLookupResponse checkKey(PixKeyType type, String key);

    PlatformPixTransferResponse createTransfer(CreatePlatformPixTransferRequest request, String idempotencyKey);

    Page<PlatformPixTransferResponse> listTransfers(Pageable pageable);

    void applyWebhookStatus(String asaasTransferId, String event);
}
