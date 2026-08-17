package com.theron.wallet.mapper;

import com.theron.wallet.dto.response.AuditLogResponse;
import com.theron.wallet.entity.AuditLog;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .organizationId(log.getOrganization() != null ? log.getOrganization().getId() : null)
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .deviceId(log.getDeviceId())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
