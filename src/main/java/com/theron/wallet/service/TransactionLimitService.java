package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateTransactionLimitRequest;
import com.theron.wallet.dto.request.UpdateTransactionLimitRequest;
import com.theron.wallet.dto.response.TransactionLimitResponse;

import java.util.List;
import java.util.UUID;

public interface TransactionLimitService {

    TransactionLimitResponse create(UUID actorUserId, CreateTransactionLimitRequest request);

    List<TransactionLimitResponse> listByOrganization(UUID actorUserId, UUID organizationId);

    TransactionLimitResponse getById(UUID actorUserId, UUID id);

    TransactionLimitResponse update(UUID actorUserId, UUID id, UpdateTransactionLimitRequest request);

    /**
     * Fail-open when no matching enabled rule exists. Must run in the same DB transaction as the debit.
     */
    void assertWithinLimits(LimitContext context);
}
