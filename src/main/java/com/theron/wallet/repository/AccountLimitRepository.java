package com.theron.wallet.repository;

import com.theron.wallet.entity.AccountLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountLimitRepository extends JpaRepository<AccountLimit, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM AccountLimit l WHERE l.accountId = :accountId")
    Optional<AccountLimit> findByAccountIdForUpdate(@Param("accountId") UUID accountId);

    Optional<AccountLimit> findByAccountId(UUID accountId);
}
