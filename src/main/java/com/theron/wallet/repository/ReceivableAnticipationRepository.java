package com.theron.wallet.repository;

import com.theron.wallet.entity.ReceivableAnticipation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceivableAnticipationRepository extends JpaRepository<ReceivableAnticipation, UUID> {

    Page<ReceivableAnticipation> findByAccount_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    Optional<ReceivableAnticipation> findByIdAndAccount_Id(UUID id, UUID accountId);
}
