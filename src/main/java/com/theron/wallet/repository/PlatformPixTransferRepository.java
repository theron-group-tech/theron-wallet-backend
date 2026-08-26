package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.enums.TransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlatformPixTransfer> findByStatusAndCreditTransactionIdIsNull(TransactionStatus status);
}
