package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformPixTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformPixTransferRepository extends JpaRepository<PlatformPixTransfer, UUID> {

    Optional<PlatformPixTransfer> findByAsaasTransferId(String asaasTransferId);

    Optional<PlatformPixTransfer> findByIdempotencyKey(String idempotencyKey);
}
