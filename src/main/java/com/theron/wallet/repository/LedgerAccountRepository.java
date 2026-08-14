package com.theron.wallet.repository;

import com.theron.wallet.entity.LedgerAccount;
import com.theron.wallet.enums.LedgerAccountKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByAccount_Id(UUID accountId);

    Optional<LedgerAccount> findByOrganization_IdAndKindAndCurrency(
            UUID organizationId, LedgerAccountKind kind, String currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LedgerAccount la WHERE la.id = :id")
    Optional<LedgerAccount> findByIdForUpdate(@Param("id") UUID id);
}
