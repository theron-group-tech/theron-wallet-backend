package com.theron.wallet.repository;

import com.theron.wallet.entity.Notification;
import com.theron.wallet.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByUser_IdAndTypeAndResourceId(UUID userId, NotificationType type, UUID resourceId);

    long countByUser_IdAndReadAtIsNull(UUID userId);

    long countByUser_IdAndOrganization_IdAndReadAtIsNull(UUID userId, UUID organizationId);

    long countByType(NotificationType type);

    @Query("""
            SELECT n FROM Notification n
            LEFT JOIN FETCH n.organization
            LEFT JOIN FETCH n.user
            WHERE n.type = :type
            ORDER BY n.createdAt DESC
            """)
    List<Notification> findByTypeWithDetails(@Param("type") NotificationType type);

    @Query(
            value = """
                    SELECT n FROM Notification n
                    WHERE n.user.id = :userId
                      AND (:unreadOnly = FALSE OR n.readAt IS NULL)
                      AND (:organizationId IS NULL OR n.organization.id = :organizationId)
                    """,
            countQuery = """
                    SELECT COUNT(n) FROM Notification n
                    WHERE n.user.id = :userId
                      AND (:unreadOnly = FALSE OR n.readAt IS NULL)
                      AND (:organizationId IS NULL OR n.organization.id = :organizationId)
                    """)
    Page<Notification> search(
            @Param("userId") UUID userId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("organizationId") UUID organizationId,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.user.id = :userId
              AND n.readAt IS NULL
              AND (:organizationId IS NULL OR n.organization.id = :organizationId)
            """)
    int markAllRead(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("readAt") LocalDateTime readAt);
}
