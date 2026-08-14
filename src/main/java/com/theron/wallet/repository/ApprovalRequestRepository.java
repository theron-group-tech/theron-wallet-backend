package com.theron.wallet.repository;

import com.theron.wallet.entity.ApprovalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    @Query("""
            SELECT r FROM ApprovalRequest r
            JOIN FETCH r.account a
            JOIN FETCH a.organization
            JOIN FETCH r.organization
            JOIN FETCH r.requestedBy
            JOIN FETCH r.transaction
            WHERE r.id = :id
            """)
    Optional<ApprovalRequest> findByIdWithDetails(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ApprovalRequest r WHERE r.id = :id")
    Optional<ApprovalRequest> findByIdForUpdate(@Param("id") UUID id);

    Optional<ApprovalRequest> findByTransaction_Id(UUID transactionId);

    @Query("""
            SELECT r FROM ApprovalRequest r
            JOIN FETCH r.transaction
            JOIN FETCH r.requestedBy
            WHERE r.account.id = :accountId
            ORDER BY r.createdAt DESC
            """)
    List<ApprovalRequest> findByAccountIdOrderByCreatedAtDesc(@Param("accountId") UUID accountId);
}
