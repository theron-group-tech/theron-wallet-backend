package com.theron.wallet.repository;

import com.theron.wallet.entity.ApprovalPolicy;
import com.theron.wallet.enums.ApprovalPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, UUID> {

    @Query("""
            SELECT p FROM ApprovalPolicy p
            JOIN FETCH p.account
            JOIN FETCH p.organization
            WHERE p.account.id = :accountId
            ORDER BY p.amountMin ASC
            """)
    List<ApprovalPolicy> findByAccountIdOrderByAmountMinAsc(@Param("accountId") UUID accountId);

    @Query("""
            SELECT p FROM ApprovalPolicy p
            JOIN FETCH p.account a
            JOIN FETCH a.organization
            JOIN FETCH p.organization
            WHERE p.account.id = :accountId
              AND p.status = :status
              AND p.amountMin <= :amount
              AND (p.amountMax IS NULL OR p.amountMax >= :amount)
            """)
    Optional<ApprovalPolicy> findActiveCoveringAmount(
            @Param("accountId") UUID accountId,
            @Param("amount") BigDecimal amount,
            @Param("status") ApprovalPolicyStatus status);

    List<ApprovalPolicy> findByAccount_IdAndStatus(UUID accountId, ApprovalPolicyStatus status);
}
