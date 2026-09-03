package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateAccountPixKeyRequest;
import com.theron.wallet.dto.request.CreateAccountPixQrCodeRequest;
import com.theron.wallet.dto.request.CreatePixPayQrCodeRequest;
import com.theron.wallet.dto.request.CreatePixTransferRequest;
import com.theron.wallet.dto.response.AccountPixKeyResponse;
import com.theron.wallet.dto.response.AccountPixQrCodeResponse;
import com.theron.wallet.dto.response.PixKeyLookupResponse;
import com.theron.wallet.dto.response.PixPayQrCodeResponse;
import com.theron.wallet.dto.response.PixTransferResponse;
import com.theron.wallet.enums.PixKeyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PixService {

    AccountPixKeyResponse createKey(UUID actorUserId, CreateAccountPixKeyRequest request);

    List<AccountPixKeyResponse> listKeys(UUID actorUserId, UUID accountId);

    void deleteKey(UUID actorUserId, UUID pixKeyId);

    PixKeyLookupResponse checkKey(UUID actorUserId, UUID accountId, PixKeyType type, String key);

    PixTransferResponse createTransfer(UUID actorUserId, CreatePixTransferRequest request);

    PixPayQrCodeResponse payQrCode(UUID actorUserId, CreatePixPayQrCodeRequest request, String idempotencyKey);

    PixPayQrCodeResponse getPixTransaction(UUID actorUserId, UUID accountId, String asaasPixTransactionId);

    /**
     * After required approvals: debit wallet, post ledger, transition to PROCESSING, call Asaas.
     */
    PixTransferResponse executeApprovedTransfer(UUID transactionId);

    PixTransferResponse getTransfer(UUID actorUserId, UUID pixTransactionId);

    Page<PixTransferResponse> listTransfers(UUID actorUserId, UUID accountId, Pageable pageable);

    AccountPixQrCodeResponse createQrCode(UUID actorUserId, CreateAccountPixQrCodeRequest request);
}
