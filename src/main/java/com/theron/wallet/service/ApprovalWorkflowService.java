package com.theron.wallet.service;

import com.theron.wallet.dto.request.ApprovalDecisionRequest;
import com.theron.wallet.dto.response.ApprovalRequestResponse;
import com.theron.wallet.entity.ApprovalRequest;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;

import java.util.List;
import java.util.UUID;

public interface ApprovalWorkflowService {

    ApprovalRequest createPendingRequest(
            Transaction transaction,
            User requestedBy,
            int requiredApprovals);

    ApprovalRequestResponse getById(UUID actorUserId, UUID approvalRequestId);

    List<ApprovalRequestResponse> listByAccount(UUID actorUserId, UUID accountId);

    ApprovalRequestResponse approve(UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest request);

    ApprovalRequestResponse reject(UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest request);

    ApprovalRequestResponse cancel(UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest request);
}
