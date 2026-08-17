package com.theron.wallet.service;

import com.theron.wallet.dto.response.AuditLogResponse;
import com.theron.wallet.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface AuditLogService {

    void record(
            AuditAction action,
            UUID organizationId,
            UUID userId,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata);

    void record(
            AuditAction action,
            UUID organizationId,
            UUID userId,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            String ip,
            String userAgent,
            String deviceId);

    Page<AuditLogResponse> list(
            UUID organizationId,
            AuditAction action,
            UUID userId,
            String resourceType,
            Pageable pageable);
}
