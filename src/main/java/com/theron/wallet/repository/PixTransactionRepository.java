package com.theron.wallet.repository;

import com.theron.wallet.entity.PixTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
