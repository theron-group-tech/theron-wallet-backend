package com.theron.wallet.service;

import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DepositService {

    DepositResponse createPixDeposit(DepositRequest request);

    PixQrCodeResponse getPixQrCode(UUID transactionId);

    DepositResponse findById(UUID transactionId);

    /** List deposits filtered by walletId or subaccountId (one must be provided). */
    Page<DepositResponse> findAll(UUID walletId, UUID subaccountId, Pageable pageable);
}
