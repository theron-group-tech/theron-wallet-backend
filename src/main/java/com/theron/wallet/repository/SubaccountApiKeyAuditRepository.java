package com.theron.wallet.repository;

import com.theron.wallet.entity.SubaccountApiKeyAudit;
import com.theron.wallet.enums.ApiKeyAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SubaccountApiKeyAuditRepository extends JpaRepository<SubaccountApiKeyAudit, UUID> {

    Page<SubaccountApiKeyAudit> findBySubaccountIdOrderByCreatedAtDesc(UUID subaccountId, Pageable pageable);

    long countBySubaccountIdAndAction(UUID subaccountId, ApiKeyAuditAction action);
}
