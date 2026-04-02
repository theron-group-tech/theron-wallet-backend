package com.theron.wallet.service;

import com.theron.wallet.dto.request.DepositRequest;
import com.theron.wallet.dto.response.DepositResponse;
import com.theron.wallet.dto.response.PixQrCodeResponse;

import java.util.UUID;

public interface DepositService {

    DepositResponse createPixDeposit(DepositRequest request);

    PixQrCodeResponse getPixQrCode(UUID transactionId);

    DepositResponse findById(UUID transactionId);
}
