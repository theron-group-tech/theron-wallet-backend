package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.AuditLogResponse;
import com.theron.wallet.entity.AuditLog;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.AuditLogMapper;
import com.theron.wallet.repository.AuditLogRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.security.ClientRequestMetadata;
import com.theron.wallet.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final String[] SENSITIVE_FRAGMENTS = {
            "password", "accesstoken", "refreshtoken", "token", "apikey", "secret", "authorization"
    };

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void record(
            AuditAction action,
            UUID organizationId,
            UUID userId,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata) {
        record(action, organizationId, userId, resourceType, resourceId, metadata, null, null, null);
    }

    @Override
    @Transactional
    public void record(
            AuditAction action,
            UUID organizationId,
            UUID userId,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            String ip,
            String userAgent,
            String deviceId) {
        HttpServletRequest request = currentRequest();
        String resolvedIp = firstNonBlank(ip, request == null ? null : ClientRequestMetadata.clientIp(request));
        String resolvedUa = firstNonBlank(userAgent, request == null ? null : ClientRequestMetadata.userAgent(request));
        String resolvedDevice = firstNonBlank(deviceId, header(request, "X-Device-Id"));

        Organization organization = null;
        if (organizationId != null) {
            organization = organizationRepository.getReferenceById(organizationId);
        }
        User user = null;
        if (userId != null) {
            user = userRepository.getReferenceById(userId);
        }

        AuditLog entry = AuditLog.builder()
                .organization(organization)
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId == null ? null : resourceId.toString())
                .ip(resolvedIp)
                .userAgent(resolvedUa)
                .deviceId(truncate(resolvedDevice, 128))
                .metadata(sanitize(metadata))
                .build();
        auditLogRepository.save(entry);
        log.debug("Audit recorded: action={}, org={}, user={}", action, organizationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            UUID organizationId,
            AuditAction action,
            UUID userId,
            String resourceType,
            Pageable pageable) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        String type = resourceType == null || resourceType.isBlank() ? null : resourceType.trim();
        return auditLogRepository.search(organizationId, action, userId, type, pageable)
                .map(AuditLogMapper::toResponse);
    }

    static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() == null || isSensitiveKey(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                Map<String, Object> sanitizedNested = sanitize(nestedMap);
                if (sanitizedNested != null) {
                    clean.put(entry.getKey(), sanitizedNested);
                }
            } else {
                clean.put(entry.getKey(), value);
            }
        }
        return clean.isEmpty() ? null : clean;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String header(HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        return request.getHeader(name);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
