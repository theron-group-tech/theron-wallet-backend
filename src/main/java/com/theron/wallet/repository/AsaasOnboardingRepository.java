package com.theron.wallet.repository;

import com.theron.wallet.entity.AsaasOnboarding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AsaasOnboardingRepository extends JpaRepository<AsaasOnboarding, UUID> {

    @Query("""
            SELECT o FROM AsaasOnboarding o
            JOIN FETCH o.account
            JOIN FETCH o.user
            WHERE o.account.id = :accountId
            """)
    Optional<AsaasOnboarding> findByAccountId(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM AsaasOnboarding o
            JOIN FETCH o.account acc
            JOIN FETCH acc.organization
            JOIN FETCH o.user
            WHERE o.account.id = :accountId
            """)
    Optional<AsaasOnboarding> findByAccountIdForUpdate(@Param("accountId") UUID accountId);

    Optional<AsaasOnboarding> findByAsaasAccountId(String asaasAccountId);
}
