package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.ApprovalActionType;
import com.theron.wallet.enums.ApprovalRequestStatus;
import com.theron.wallet.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalRequestResponse {

    private UUID id;
    private UUID transactionId;
    private UUID accountId;
    private UUID organizationId;
    private UUID requestedByUserId;
    private BigDecimal amount;
    private TransactionStatus transactionStatus;
    private int requiredApprovals;
    private int approvedCount;
    private ApprovalRequestStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ApprovalActionResponse> actions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApprovalActionResponse {
        private UUID id;
        private UUID actorUserId;
        private ApprovalActionType action;
        private String comment;
        private LocalDateTime createdAt;
    }
}
