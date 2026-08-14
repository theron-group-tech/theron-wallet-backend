package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.ApprovalPolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalPolicyResponse {

    private UUID id;
    private UUID accountId;
    private UUID organizationId;
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    private int requiredApprovals;
    private ApprovalPolicyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
