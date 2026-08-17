package com.theron.wallet.service.impl;

import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.UnreadCountResponse;
import com.theron.wallet.entity.Notification;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.enums.NotificationType;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.NotificationMapper;
import com.theron.wallet.notification.NotificationDispatcher;
import com.theron.wallet.repository.MembershipRoleRepository;
import com.theron.wallet.repository.NotificationRepository;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String[] SENSITIVE_FRAGMENTS = {
            "password", "accesstoken", "refreshtoken", "token", "apikey", "secret", "authorization"
    };

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final NotificationDispatcher dispatcher;

    @Override
    @Transactional
    public void notify(
            UUID userId,
            UUID organizationId,
            NotificationType type,
            UUID resourceId,
            Map<String, Object> data) {
        if (userId == null || type == null) {
            return;
        }
        if (resourceId != null
                && notificationRepository.existsByUser_IdAndTypeAndResourceId(userId, type, resourceId)) {
            log.debug("Skipping duplicate notification: type={}, user={}, resource={}", type, userId, resourceId);
            return;
        }

        Organization organization = null;
        if (organizationId != null) {
            organization = organizationRepository.getReferenceById(organizationId);
        }
        User user = userRepository.getReferenceById(userId);

        Notification notification = Notification.builder()
                .user(user)
                .organization(organization)
                .type(type)
                .title(type.title())
                .message(type.message())
                .data(sanitize(data))
                .resourceId(resourceId)
                .build();
        notification = notificationRepository.save(notification);
        dispatcher.dispatch(notification);
        log.debug("Notification created: type={}, user={}, id={}", type, userId, notification.getId());
    }

    @Override
    @Transactional
    public void notifyUsersWithPermission(
            UUID organizationId,
            String permissionCode,
            UUID excludeUserId,
            NotificationType type,
            UUID resourceId,
            Map<String, Object> data) {
        if (organizationId == null || permissionCode == null) {
            return;
        }
        List<UUID> userIds = membershipRoleRepository.findUserIdsByOrganizationAndPermission(
                organizationId, permissionCode);
        for (UUID userId : userIds) {
            if (excludeUserId != null && excludeUserId.equals(userId)) {
                continue;
            }
            notify(userId, organizationId, type, resourceId, data);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(
            UUID actorUserId,
            UUID organizationId,
            boolean unreadOnly,
            Pageable pageable) {
        assertOrgMembershipIfPresent(actorUserId, organizationId);
        return notificationRepository.search(actorUserId, unreadOnly, organizationId, pageable)
                .map(NotificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID actorUserId, UUID organizationId) {
        assertOrgMembershipIfPresent(actorUserId, organizationId);
        long count = organizationId == null
                ? notificationRepository.countByUser_IdAndReadAtIsNull(actorUserId)
                : notificationRepository.countByUser_IdAndOrganization_IdAndReadAtIsNull(actorUserId, organizationId);
        return UnreadCountResponse.builder().count(count).build();
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID actorUserId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!notification.getUser().getId().equals(actorUserId)) {
            throw new ForbiddenException("Cannot access another user's notification");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public UnreadCountResponse markAllRead(UUID actorUserId, UUID organizationId) {
        assertOrgMembershipIfPresent(actorUserId, organizationId);
        notificationRepository.markAllRead(actorUserId, organizationId, LocalDateTime.now());
        return unreadCount(actorUserId, organizationId);
    }

    private void assertOrgMembershipIfPresent(UUID actorUserId, UUID organizationId) {
        if (organizationId == null) {
            return;
        }
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organization", "id", organizationId);
        }
        if (!membershipRepository.existsByOrganizationIdAndUserIdAndStatusIn(
                organizationId, actorUserId, List.of(MembershipStatus.ACTIVE))) {
            throw new ForbiddenException("Not an active member of this organization");
        }
    }

    static Map<String, Object> sanitize(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
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
}
