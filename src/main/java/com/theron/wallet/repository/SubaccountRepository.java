package com.theron.wallet.repository;

import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.SubaccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubaccountRepository extends JpaRepository<Subaccount, UUID> {

    Optional<Subaccount> findByCustomerId(UUID customerId);

    Optional<Subaccount> findByAsaasAccountId(String asaasAccountId);

    Optional<Subaccount> findByWebhookToken(String webhookToken);

    boolean existsByCustomerId(UUID customerId);

    List<Subaccount> findByStatus(SubaccountStatus status);
}
