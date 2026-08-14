package com.theron.wallet.repository;

import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.SubaccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubaccountRepository extends JpaRepository<Subaccount, UUID> {

    /** Primary lookup — by CPF/CNPJ (unique identifier for standalone subaccounts). */
    Optional<Subaccount> findByCpfCnpj(String cpfCnpj);

    Optional<Subaccount> findByAsaasAccountId(String asaasAccountId);

    Optional<Subaccount> findByWebhookToken(String webhookToken);

    @Query("""
            SELECT s FROM Subaccount s
            JOIN FETCH s.account a
            JOIN FETCH a.organization
            WHERE a.id = :accountId
            """)
    Optional<Subaccount> findByAccount_Id(@Param("accountId") UUID accountId);

    boolean existsByCpfCnpj(String cpfCnpj);

    List<Subaccount> findByStatus(SubaccountStatus status);

    /** Paginated list — optional status filter. */
    Page<Subaccount> findByStatus(SubaccountStatus status, Pageable pageable);
}
