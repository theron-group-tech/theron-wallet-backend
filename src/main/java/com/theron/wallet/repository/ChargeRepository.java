package com.theron.wallet.repository;

import com.theron.wallet.entity.Charge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByAccount_IdAndExternalReference(UUID accountId, String externalReference);

    Optional<Charge> findByAsaasPaymentId(String asaasPaymentId);

    Optional<Charge> findByIdAndAccount_Id(UUID id, UUID accountId);

    Page<Charge> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    @Query("""
            SELECT c FROM Charge c
            LEFT JOIN FETCH c.billingCustomer
            LEFT JOIN FETCH c.splits
            WHERE c.id = :id AND c.account.id = :accountId
            """)
    Optional<Charge> findByIdAndAccountIdWithDetails(
            @Param("id") UUID id, @Param("accountId") UUID accountId);
}
