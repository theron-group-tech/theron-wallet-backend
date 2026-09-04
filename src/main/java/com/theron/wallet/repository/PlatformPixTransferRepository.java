package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformPixTransferRepository extends JpaRepository<PlatformPixTransfer, UUID> {

    Optional<PlatformPixTransfer> findByAsaasTransferId(String asaasTransferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlatformPixTransfer p WHERE p.asaasTransferId = :asaasTransferId")
    Optional<PlatformPixTransfer> findByAsaasTransferIdForUpdate(
            @Param("asaasTransferId") String asaasTransferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlatformPixTransfer p WHERE p.id = :id")
    Optional<PlatformPixTransfer> findByIdForUpdate(@Param("id") UUID id);

    Optional<PlatformPixTransfer> findByIdempotencyKey(String idempotencyKey);

    Optional<PlatformPixTransfer> findByAsaasPixTransactionId(String asaasPixTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlatformPixTransfer> findByStatusAndCreditTransactionIdIsNull(TransactionStatus status);

    @Query("""
            SELECT p FROM PlatformPixTransfer p
            WHERE p.destinationPixKey = :destinationKey
              AND p.amount = :amount
              AND p.status = :status
              AND p.creditTransactionId IS NOT NULL
              AND p.createdAt >= :since
            ORDER BY p.createdAt DESC
            """)
    List<PlatformPixTransfer> findCreditedByDestinationKeyAndAmount(
            @Param("destinationKey") String destinationKey,
            @Param("amount") BigDecimal amount,
            @Param("status") TransactionStatus status,
            @Param("since") LocalDateTime since);

    @Query("""
            SELECT p FROM PlatformPixTransfer p
            WHERE p.destinationPixKey IN :destinationKeys
              AND p.status = :status
              AND p.creditTransactionId IS NOT NULL
            ORDER BY p.createdAt ASC
            """)
    List<PlatformPixTransfer> findCreditedByDestinationKeys(
            @Param("destinationKeys") Collection<String> destinationKeys,
            @Param("status") TransactionStatus status);

    @Query("""
            SELECT p FROM PlatformPixTransfer p
            WHERE p.asaasTransferId IS NULL
              AND p.destinationPixKey = :destinationKey
              AND p.status IN :statuses
              AND p.amount = :amount
              AND p.createdAt >= :since
            ORDER BY p.createdAt DESC
            """)
    List<PlatformPixTransfer> findPendingQrPayForBind(
            @Param("destinationKey") String destinationKey,
            @Param("statuses") Collection<TransactionStatus> statuses,
            @Param("amount") BigDecimal amount,
            @Param("since") LocalDateTime since);
}
