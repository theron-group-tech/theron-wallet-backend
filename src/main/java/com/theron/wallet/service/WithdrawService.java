package com.theron.wallet.service;

import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;

import java.util.UUID;

public interface WithdrawService {

    WithdrawResponse createWithdraw(WithdrawRequest request);

    WithdrawResponse findById(UUID transactionId);
}
