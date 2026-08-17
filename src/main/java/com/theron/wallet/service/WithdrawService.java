package com.theron.wallet.service;

import com.theron.wallet.dto.request.WithdrawRequest;
import com.theron.wallet.dto.response.WithdrawResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WithdrawService {

    WithdrawResponse createWithdraw(UUID actorUserId, WithdrawRequest request);

    WithdrawResponse findById(UUID transactionId);

    /** List withdrawals filtered by walletId or subaccountId (one must be provided). */
    Page<WithdrawResponse> findAll(UUID walletId, UUID subaccountId, Pageable pageable);
}
