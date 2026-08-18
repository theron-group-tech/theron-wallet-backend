package com.theron.wallet.controller;

import com.theron.wallet.dto.response.AuditLogResponse;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Append-only audit trail. Requires JWT access token.")
public class AuditLogController {
    private final AuditLogService auditLogService;
    private final ActorResolver actorResolver;
    private final AuthorizationService authorizationService;

    @GetMapping
    @Operation(summary = "List audit logs for an organization", description = "Requires audit.read")
    public ResponseEntity<Page<AuditLogResponse>> list(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String resourceType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID actor = actorResolver.requireProductUserId();
        authorizationService.requirePermission(organizationId, actor, PermissionCodes.AUDIT_READ);
        return ResponseEntity.ok(auditLogService.list(
                organizationId,
                action,
                userId,
                resourceType,
                pageable));
    }
}
