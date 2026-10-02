package com.theron.wallet.repository;

import com.theron.wallet.entity.PartnerWebhookOutbox;
import com.theron.wallet.enums.PartnerWebhookOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerWebhookOutboxRepository extends JpaRepository<PartnerWebhookOutbox, UUID> {

    Optional<PartnerWebhookOutbox> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT o FROM PartnerWebhookOutbox o
            WHERE (o.status IN :statuses AND o.nextAttemptAt <= :now)
               OR (o.status = :delivering AND o.updatedAt <= :staleBefore)
            ORDER BY o.nextAttemptAt ASC
            """)
    List<PartnerWebhookOutbox> findDueForDelivery(
            @Param("statuses") List<PartnerWebhookOutboxStatus> statuses,
            @Param("delivering") PartnerWebhookOutboxStatus delivering,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable);
}
