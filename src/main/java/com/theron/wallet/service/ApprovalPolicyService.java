package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateApprovalPolicyRequest;
import com.theron.wallet.dto.response.ApprovalPolicyResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ApprovalPolicyService {

    ApprovalPolicyResponse create(UUID actorUserId, CreateApprovalPolicyRequest request);

    List<ApprovalPolicyResponse> listByAccount(UUID actorUserId, UUID accountId);

    /**
     * Returns required approver count for amount. Throws 422 if no ACTIVE policy covers the amount.
     */
    int resolveRequiredApprovals(UUID accountId, BigDecimal amount);
}
