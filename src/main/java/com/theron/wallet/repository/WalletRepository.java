package com.theron.wallet.repository;

import com.theron.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findBySubaccountId(UUID subaccountId);

    Optional<Wallet> findByAccount_Id(UUID accountId);

    /** Compatibility alias used by payment-order service. */
    default Optional<Wallet> findByAccountId(UUID accountId) {
        return findByAccount_Id(accountId);
    }

    List<Wallet> findByAccount_IdIn(Collection<UUID> accountIds);

    Page<Wallet> findByAccount_IdIn(Collection<UUID> accountIds, Pageable pageable);

    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.account.id IN :accountIds")
    BigDecimal sumBalanceByAccountIds(@Param("accountIds") Collection<UUID> accountIds);

    boolean existsBySubaccountId(UUID subaccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.subaccount.id = :subaccountId")
    Optional<Wallet> findBySubaccountIdWithLock(@Param("subaccountId") UUID subaccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.account.id = :accountId")
    Optional<Wallet> findByAccountIdWithLock(@Param("accountId") UUID accountId);
}
