package com.theron.wallet.repository;

import com.theron.wallet.entity.Transaction;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.enums.TransactionType;
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
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);

    Page<Transaction> findByWalletIdAndType(UUID walletId, TransactionType type, Pageable pageable);

    Page<Transaction> findByWalletIdAndStatus(UUID walletId, TransactionStatus status, Pageable pageable);

    Optional<Transaction> findByAsaasPaymentId(String asaasPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.asaasPaymentId = :asaasPaymentId")
    Optional<Transaction> findByAsaasPaymentIdForUpdate(@Param("asaasPaymentId") String asaasPaymentId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByExternalReference(String externalReference);
}
