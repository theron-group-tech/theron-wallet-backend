package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreatePlatformPixKeyRequest;
import com.theron.wallet.dto.request.CreatePlatformPixPayQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixTransferRequest;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
import com.theron.wallet.dto.response.PlatformPixPayQrCodeResponse;
import com.theron.wallet.dto.response.PlatformPixKeyResponse;
import com.theron.wallet.dto.response.PlatformPixTransferResponse;
import com.theron.wallet.enums.PixKeyType;
import com.theron.wallet.entity.PlatformPixTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PlatformPixService {

    List<PlatformPixKeyResponse> listKeys();

    PlatformPixKeyResponse createKey(CreatePlatformPixKeyRequest request);

    void deleteKey(String asaasPixKeyId);

    PixKeyLookupResponse checkKey(PixKeyType type, String key);

    AccountPixQrCodeResponse createQrCode(CreatePlatformPixQrCodeRequest request);

    PlatformPixPayQrCodeResponse payQrCode(CreatePlatformPixPayQrCodeRequest request, String idempotencyKey);

    PlatformPixPayQrCodeResponse getPixTransaction(String asaasPixTransactionId);

    PlatformPixTransferResponse createTransfer(CreatePlatformPixTransferRequest request, String idempotencyKey);

    Page<PlatformPixTransferResponse> listTransfers(Pageable pageable);

    Optional<PlatformPixTransfer> bindAndFindPlatformTransferForValidation(
            String transferId, String externalReference, BigDecimal amount);

    void applyWebhookStatus(
            String asaasTransferId, String event, String externalReference, BigDecimal value);

    int reconcileCredits();
}
