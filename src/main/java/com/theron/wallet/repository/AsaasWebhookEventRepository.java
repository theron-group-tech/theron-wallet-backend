package com.theron.wallet.repository;

import com.theron.wallet.entity.AsaasWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsaasWebhookEventRepository extends JpaRepository<AsaasWebhookEvent, UUID> {

    Optional<AsaasWebhookEvent> findByAsaasEventId(String asaasEventId);

    boolean existsByAsaasEventId(String asaasEventId);
}
