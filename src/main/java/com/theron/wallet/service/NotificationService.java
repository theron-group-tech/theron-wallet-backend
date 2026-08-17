package com.theron.wallet.service;

import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.UnreadCountResponse;
import com.theron.wallet.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    void notify(
            UUID userId,
            UUID organizationId,
            NotificationType type,
            UUID resourceId,
            Map<String, Object> data);

    void notifyUsersWithPermission(
            UUID organizationId,
            String permissionCode,
            UUID excludeUserId,
            NotificationType type,
            UUID resourceId,
            Map<String, Object> data);

    Page<NotificationResponse> list(
            UUID actorUserId,
            UUID organizationId,
            boolean unreadOnly,
            Pageable pageable);

    UnreadCountResponse unreadCount(UUID actorUserId, UUID organizationId);

    NotificationResponse markRead(UUID actorUserId, UUID notificationId);

    UnreadCountResponse markAllRead(UUID actorUserId, UUID organizationId);
}
