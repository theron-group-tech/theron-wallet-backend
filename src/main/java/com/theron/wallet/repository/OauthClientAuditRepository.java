package com.theron.wallet.repository;

import com.theron.wallet.entity.OauthClientAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OauthClientAuditRepository extends JpaRepository<OauthClientAudit, UUID> {
}
