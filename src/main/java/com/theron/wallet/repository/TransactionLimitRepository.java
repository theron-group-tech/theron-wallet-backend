package com.theron.wallet.repository;

import com.theron.wallet.entity.TransactionLimit;
import com.theron.wallet.enums.LimitTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, UUID> {

    @Query("""
            SELECT l FROM TransactionLimit l
            JOIN FETCH l.organization
            LEFT JOIN FETCH l.account
            LEFT JOIN FETCH l.user
            LEFT JOIN FETCH l.role
            WHERE l.id = :id
            """)
    java.util.Optional<TransactionLimit> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT l FROM TransactionLimit l
            JOIN FETCH l.organization
            LEFT JOIN FETCH l.account
            LEFT JOIN FETCH l.user
            LEFT JOIN FETCH l.role
            WHERE l.organization.id = :organizationId
            ORDER BY l.createdAt DESC
            """)
    List<TransactionLimit> findByOrganizationIdOrderByCreatedAtDesc(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT l FROM TransactionLimit l
            JOIN FETCH l.organization
            LEFT JOIN FETCH l.account
            LEFT JOIN FETCH l.user
            LEFT JOIN FETCH l.role
            WHERE l.organization.id = :organizationId
              AND l.transactionType = :type
              AND l.enabled = true
            """)
    List<TransactionLimit> findEnabledByOrganizationAndType(
            @Param("organizationId") UUID organizationId,
            @Param("type") LimitTransactionType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM TransactionLimit l WHERE l.id IN :ids")
    List<TransactionLimit> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
