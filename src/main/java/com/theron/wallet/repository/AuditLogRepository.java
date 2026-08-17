package com.theron.wallet.repository;

import com.theron.wallet.entity.AuditLog;
import com.theron.wallet.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query(
            value = """
                    SELECT a FROM AuditLog a
                    WHERE a.organization.id = :organizationId
                      AND (:action IS NULL OR a.action = :action)
                      AND (:userId IS NULL OR a.user.id = :userId)
                      AND (:resourceType IS NULL OR a.resourceType = :resourceType)
                    """,
            countQuery = """
                    SELECT COUNT(a) FROM AuditLog a
                    WHERE a.organization.id = :organizationId
                      AND (:action IS NULL OR a.action = :action)
                      AND (:userId IS NULL OR a.user.id = :userId)
                      AND (:resourceType IS NULL OR a.resourceType = :resourceType)
                    """)
    Page<AuditLog> search(
            @Param("organizationId") UUID organizationId,
            @Param("action") AuditAction action,
            @Param("userId") UUID userId,
            @Param("resourceType") String resourceType,
            Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN FETCH a.organization
            LEFT JOIN FETCH a.user
            WHERE a.action = :action
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByActionWithDetails(@Param("action") AuditAction action);

    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN FETCH a.organization
            LEFT JOIN FETCH a.user
            WHERE a.user.id = :userId
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> findByUserIdWithDetails(@Param("userId") UUID userId);
}
