package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreatePixKeyRequest;
import com.theron.wallet.dto.request.CreatePixStaticQrCodeRequest;
import com.theron.wallet.dto.response.PixKeyResponse;
import com.theron.wallet.dto.response.PixStaticQrCodeResponse;

import java.util.List;
import java.util.UUID;

public interface PixKeyService {

    PixKeyResponse createPixKey(UUID subaccountId, CreatePixKeyRequest request);

    List<PixKeyResponse> listPixKeys(UUID subaccountId);

    void deletePixKey(UUID subaccountId, String pixKeyId);

    PixStaticQrCodeResponse createStaticQrCode(
            UUID subaccountId, String pixKeyId, CreatePixStaticQrCodeRequest request);

    void deleteStaticQrCode(UUID subaccountId, String qrCodeId);
}
