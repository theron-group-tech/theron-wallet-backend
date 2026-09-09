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
import com.theron.wallet.security.Actor;

import java.util.UUID;

public interface PixService {

    AccountPixKeyResponse createKey(Actor actor, CreateAccountPixKeyRequest request);

    List<AccountPixKeyResponse> listKeys(Actor actor, UUID accountId);

    void deleteKey(Actor actor, UUID pixKeyId);

    PixKeyLookupResponse checkKey(Actor actor, UUID accountId, PixKeyType type, String key);

    PixTransferResponse createTransfer(Actor actor, CreatePixTransferRequest request);

    PixPayQrCodeResponse payQrCode(Actor actor, CreatePixPayQrCodeRequest request, String idempotencyKey);

    PixPayQrCodeResponse getPixTransaction(Actor actor, UUID accountId, String asaasPixTransactionId);

    /**
     * After required approvals: debit wallet, post ledger, transition to PROCESSING, call Asaas.
     */
    PixTransferResponse executeApprovedTransfer(UUID transactionId);

    PixTransferResponse getTransfer(Actor actor, UUID pixTransactionId);

    Page<PixTransferResponse> listTransfers(Actor actor, UUID accountId, Pageable pageable);

    AccountPixQrCodeResponse createQrCode(Actor actor, CreateAccountPixQrCodeRequest request);
}
