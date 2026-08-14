package com.theron.wallet.repository;

import com.theron.wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransaction_Id(UUID transactionId);

    @Query("SELECT e FROM LedgerEntry e JOIN FETCH e.ledgerAccount WHERE e.transaction.id = :transactionId")
    List<LedgerEntry> findByTransactionIdWithAccount(@Param("transactionId") UUID transactionId);

    long countByTransaction_Id(UUID transactionId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = com.theron.wallet.enums.LedgerDirection.CREDIT THEN e.amount ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN e.direction = com.theron.wallet.enums.LedgerDirection.DEBIT THEN e.amount ELSE 0 END), 0)
            FROM LedgerEntry e
            WHERE e.ledgerAccount.id = :ledgerAccountId
            """)
    BigDecimal reconstructBalanceByLedgerAccountId(@Param("ledgerAccountId") UUID ledgerAccountId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = com.theron.wallet.enums.LedgerDirection.CREDIT THEN e.amount ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN e.direction = com.theron.wallet.enums.LedgerDirection.DEBIT THEN e.amount ELSE 0 END), 0)
            FROM LedgerEntry e
            WHERE e.ledgerAccount.account.id = :accountId
            """)
    BigDecimal reconstructBalanceByAccountId(@Param("accountId") UUID accountId);
}
