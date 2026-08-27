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
                .sourceAccountId(order.getSourceAccount() != null ? order.getSourceAccount().getId() : null)
                .destinationAccountId(order.getDestinationAccount().getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .description(order.getDescription())
                .status(order.getStatus())
                .createdByUserId(order.getCreatedBy().getId())
                .createdByName(order.getCreatedBy().getName())
                .decidedByUserId(order.getDecidedBy() != null ? order.getDecidedBy().getId() : null)
                .decidedByName(order.getDecidedBy() != null ? order.getDecidedBy().getName() : null)
                .destinationOwnerName(order.getDestinationAccount().getOwnerUser() != null
                        ? order.getDestinationAccount().getOwnerUser().getName()
                        : null)
                .destinationAccountName(order.getDestinationAccount().getName())
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
