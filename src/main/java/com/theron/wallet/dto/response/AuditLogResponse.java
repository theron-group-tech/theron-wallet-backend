package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {

    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private AuditAction action;
    private String resourceType;
    private String resourceId;
    private String ip;
    private String userAgent;
    private String deviceId;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
