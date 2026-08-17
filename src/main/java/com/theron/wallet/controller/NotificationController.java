package com.theron.wallet.controller;

import com.theron.wallet.dto.response.NotificationResponse;
import com.theron.wallet.dto.response.UnreadCountResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app inbox. Actor from JWT or X-Actor-User-Id.")
public class NotificationController {

    public static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final NotificationService notificationService;
    private final ActorResolver actorResolver;

    @GetMapping
    @Operation(summary = "List notifications for the current user")
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationService.list(
                actorResolver.requireProductUserId(actorUserId),
                organizationId,
                unreadOnly,
                pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam(required = false) UUID organizationId) {
        return ResponseEntity.ok(notificationService.unreadCount(
                actorResolver.requireProductUserId(actorUserId), organizationId));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationResponse> markRead(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(
                actorResolver.requireProductUserId(actorUserId), id));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<UnreadCountResponse> markAllRead(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam(required = false) UUID organizationId) {
        return ResponseEntity.ok(notificationService.markAllRead(
                actorResolver.requireProductUserId(actorUserId), organizationId));
    }
}
