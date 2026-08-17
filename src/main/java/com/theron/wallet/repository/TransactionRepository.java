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

    /** All transactions belonging to a subaccount (through wallet join). */
    Page<Transaction> findByWallet_Subaccount_Id(UUID subaccountId, Pageable pageable);

    /** Transactions of a specific type for a subaccount. */
    Page<Transaction> findByWallet_Subaccount_IdAndType(UUID subaccountId, TransactionType type, Pageable pageable);

    Optional<Transaction> findByAsaasPaymentId(String asaasPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.asaasPaymentId = :asaasPaymentId")
    Optional<Transaction> findByAsaasPaymentIdForUpdate(@Param("asaasPaymentId") String asaasPaymentId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.idempotencyKey = :idempotencyKey")
    Optional<Transaction> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByBeneficiary_Id(UUID beneficiaryId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.type = com.theron.wallet.enums.TransactionType.PIX
              AND t.status IN (
                  com.theron.wallet.enums.TransactionStatus.PENDING,
                  com.theron.wallet.enums.TransactionStatus.PENDING_APPROVAL,
                  com.theron.wallet.enums.TransactionStatus.PROCESSING,
                  com.theron.wallet.enums.TransactionStatus.COMPLETED
              )
              AND t.createdAt >= :dayStart
              AND t.createdAt < :dayEnd
              AND (:excludeId IS NULL OR t.id <> :excludeId)
            """)
    java.math.BigDecimal sumPixAmountForAccountOnDay(
            @Param("accountId") UUID accountId,
            @Param("dayStart") java.time.LocalDateTime dayStart,
            @Param("dayEnd") java.time.LocalDateTime dayEnd,
            @Param("excludeId") UUID excludeId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.organization.id = :organizationId
              AND t.type = :type
              AND t.status IN (
                  com.theron.wallet.enums.TransactionStatus.PENDING,
                  com.theron.wallet.enums.TransactionStatus.PENDING_APPROVAL,
                  com.theron.wallet.enums.TransactionStatus.PROCESSING,
                  com.theron.wallet.enums.TransactionStatus.COMPLETED
              )
              AND t.createdAt >= :periodStart
              AND t.createdAt < :periodEnd
              AND (:excludeId IS NULL OR t.id <> :excludeId)
            """)
    java.math.BigDecimal sumByOrganizationAndTypeInPeriod(
            @Param("organizationId") UUID organizationId,
            @Param("type") TransactionType type,
            @Param("periodStart") java.time.LocalDateTime periodStart,
            @Param("periodEnd") java.time.LocalDateTime periodEnd,
            @Param("excludeId") UUID excludeId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.type = :type
              AND t.status IN (
                  com.theron.wallet.enums.TransactionStatus.PENDING,
                  com.theron.wallet.enums.TransactionStatus.PENDING_APPROVAL,
                  com.theron.wallet.enums.TransactionStatus.PROCESSING,
                  com.theron.wallet.enums.TransactionStatus.COMPLETED
              )
              AND t.createdAt >= :periodStart
              AND t.createdAt < :periodEnd
              AND (:excludeId IS NULL OR t.id <> :excludeId)
            """)
    java.math.BigDecimal sumByAccountAndTypeInPeriod(
            @Param("accountId") UUID accountId,
            @Param("type") TransactionType type,
            @Param("periodStart") java.time.LocalDateTime periodStart,
            @Param("periodEnd") java.time.LocalDateTime periodEnd,
            @Param("excludeId") UUID excludeId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.createdBy.id = :userId
              AND t.type = :type
              AND t.status IN (
                  com.theron.wallet.enums.TransactionStatus.PENDING,
                  com.theron.wallet.enums.TransactionStatus.PENDING_APPROVAL,
                  com.theron.wallet.enums.TransactionStatus.PROCESSING,
                  com.theron.wallet.enums.TransactionStatus.COMPLETED
              )
              AND t.createdAt >= :periodStart
              AND t.createdAt < :periodEnd
              AND (:excludeId IS NULL OR t.id <> :excludeId)
            """)
    java.math.BigDecimal sumByCreatedByAndTypeInPeriod(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("periodStart") java.time.LocalDateTime periodStart,
            @Param("periodEnd") java.time.LocalDateTime periodEnd,
            @Param("excludeId") UUID excludeId);

    Optional<Transaction> findByExternalReference(String externalReference);
}
