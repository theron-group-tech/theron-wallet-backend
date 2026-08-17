package com.theron.wallet.service;

import com.theron.wallet.entity.AsaasReconciliation;
import com.theron.wallet.enums.ReconciliationKind;

import java.util.UUID;

public interface ReconciliationService {

    AsaasReconciliation reconcile(UUID transactionId);

    AsaasReconciliation reconcileAsaasResource(String asaasId, ReconciliationKind kind);
}
