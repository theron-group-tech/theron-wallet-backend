package com.theron.wallet.repository;

import com.theron.wallet.entity.PixTransaction;
import com.theron.wallet.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixTransactionRepository extends JpaRepository<PixTransaction, UUID> {

    @Query("""
            SELECT p FROM PixTransaction p
            JOIN FETCH p.account a
            JOIN FETCH a.organization
            JOIN FETCH p.transaction
            WHERE p.id = :id
            """)
    Optional<PixTransaction> findByIdWithDetails(@Param("id") UUID id);

    @Query("""
            SELECT p FROM PixTransaction p
            JOIN FETCH p.transaction
            WHERE p.transaction.id = :transactionId
            """)
    Optional<PixTransaction> findByTransactionId(@Param("transactionId") UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PixTransaction p WHERE p.transaction.id = :transactionId")
    Optional<PixTransaction> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    Page<PixTransaction> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Optional<PixTransaction> findByAsaasPixTransactionId(String asaasPixTransactionId);

    @Query("""
            SELECT p FROM PixTransaction p
            JOIN FETCH p.transaction
            WHERE p.asaasPixTransactionId = :asaasPixTransactionId
            """)
    Optional<PixTransaction> findByAsaasPixTransactionIdWithTransaction(
            @Param("asaasPixTransactionId") String asaasPixTransactionId);

    @Query("""
            SELECT p FROM PixTransaction p
            JOIN FETCH p.transaction
            WHERE p.asaasPixTransactionId IS NULL
              AND p.destinationPixKey = :destinationKey
              AND p.status IN :statuses
              AND p.transaction.amount = :amount
              AND p.createdAt >= :since
            ORDER BY p.createdAt DESC
            """)
    List<PixTransaction> findPendingQrPayForBind(
            @Param("destinationKey") String destinationKey,
            @Param("statuses") Collection<TransactionStatus> statuses,
            @Param("amount") BigDecimal amount,
            @Param("since") LocalDateTime since);
}
