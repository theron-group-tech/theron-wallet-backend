package com.theron.wallet.repository;

import com.theron.wallet.entity.AsaasReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AsaasReconciliationRepository extends JpaRepository<AsaasReconciliation, UUID> {
}
