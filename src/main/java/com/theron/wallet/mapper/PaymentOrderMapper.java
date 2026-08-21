package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.entity.PaymentOrder;

public final class PaymentOrderMapper {

    private PaymentOrderMapper() {
    }

    public static PaymentOrderResponse toResponse(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .organizationId(order.getOrganization().getId())
                .sourceAccountId(order.getSourceAccount().getId())
                .destinationAccountId(order.getDestinationAccount().getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .description(order.getDescription())
                .status(order.getStatus())
                .createdByUserId(order.getCreatedBy().getId())
                .decidedByUserId(order.getDecidedBy() != null ? order.getDecidedBy().getId() : null)
                .decisionComment(order.getDecisionComment())
                .debitTransactionId(order.getDebitTransaction() != null ? order.getDebitTransaction().getId() : null)
                .creditTransactionId(order.getCreditTransaction() != null ? order.getCreditTransaction().getId() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .decidedAt(order.getDecidedAt())
                .completedAt(order.getCompletedAt())
                .build();
    }
}
