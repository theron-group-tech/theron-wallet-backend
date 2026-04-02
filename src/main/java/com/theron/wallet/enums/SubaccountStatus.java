package com.theron.wallet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubaccountStatus {

    PROVISIONING("Subaccount creation in progress"),
    PENDING_EVALUATION("Awaiting Asaas regulatory evaluation"),
    ACTIVE("Fully operational"),
    FAILED("Provisioning failed"),
    EVALUATION_BLOCKED("Blocked during Asaas evaluation — read-only, no outbound operations"),
    SUSPENDED("Administratively suspended");

    private final String description;

    /**
     * Statuses that allow creating charges and other outbound operations in Asaas.
     * EVALUATION_BLOCKED explicitly does NOT allow outbound operations per domain rule.
     */
    public boolean allowsOutboundOperations() {
        return this == ACTIVE || this == PENDING_EVALUATION;
    }

    /**
     * Statuses that allow inbound webhook processing and read/reconciliation flows.
     */
    public boolean allowsInboundProcessing() {
        return this == ACTIVE || this == PENDING_EVALUATION || this == EVALUATION_BLOCKED;
    }

    public boolean isTerminal() {
        return this == FAILED;
    }
}
