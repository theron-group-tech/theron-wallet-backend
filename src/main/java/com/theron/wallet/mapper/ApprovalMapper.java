package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.ApprovalPolicyResponse;
import com.theron.wallet.dto.response.ApprovalRequestResponse;
import com.theron.wallet.entity.ApprovalAction;
import com.theron.wallet.entity.ApprovalPolicy;
import com.theron.wallet.entity.ApprovalRequest;

import java.util.List;

public final class ApprovalMapper {

    private ApprovalMapper() {
    }

    public static ApprovalPolicyResponse toPolicyResponse(ApprovalPolicy policy) {
        return ApprovalPolicyResponse.builder()
                .id(policy.getId())
                .accountId(policy.getAccount().getId())
                .organizationId(policy.getOrganization().getId())
                .amountMin(policy.getAmountMin())
                .amountMax(policy.getAmountMax())
                .requiredApprovals(policy.getRequiredApprovals())
                .status(policy.getStatus())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    public static ApprovalRequestResponse toRequestResponse(ApprovalRequest request, List<ApprovalAction> actions) {
        return ApprovalRequestResponse.builder()
                .id(request.getId())
                .transactionId(request.getTransaction().getId())
                .accountId(request.getAccount().getId())
                .organizationId(request.getOrganization().getId())
                .requestedByUserId(request.getRequestedBy().getId())
                .amount(request.getTransaction().getAmount())
                .transactionStatus(request.getTransaction().getStatus())
                .requiredApprovals(request.getRequiredApprovals())
                .approvedCount(request.getApprovedCount())
                .status(request.getStatus())
                .expiresAt(request.getExpiresAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .actions(actions == null ? List.of() : actions.stream().map(ApprovalMapper::toActionResponse).toList())
                .build();
    }

    public static ApprovalRequestResponse.ApprovalActionResponse toActionResponse(ApprovalAction action) {
        return ApprovalRequestResponse.ApprovalActionResponse.builder()
                .id(action.getId())
                .actorUserId(action.getActor().getId())
                .action(action.getAction())
                .comment(action.getComment())
                .createdAt(action.getCreatedAt())
                .build();
    }
}
