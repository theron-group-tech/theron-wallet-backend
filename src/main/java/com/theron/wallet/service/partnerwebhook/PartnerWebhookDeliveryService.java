package com.theron.wallet.service.partnerwebhook;

import com.theron.wallet.entity.PartnerWebhookOutbox;
import com.theron.wallet.enums.PartnerWebhookOutboxStatus;
import com.theron.wallet.repository.PartnerWebhookOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerWebhookDeliveryService {

    private final PartnerWebhookOutboxRepository outboxRepository;
    private final PartnerWebhookClient partnerWebhookClient;
    private final PlatformTransactionManager transactionManager;

    @Value("${platform.partner-webhook.max-attempts:10}")
    private int maxAttempts;

    @Value("${platform.partner-webhook.batch-size:25}")
    private int batchSize;

    @Value("${platform.partner-webhook.stale-delivering-seconds:120}")
    private int staleDeliveringSeconds;

    public void deliverDueBatch() {
        List<UUID> dueIds = claimDueIds();
        for (UUID id : dueIds) {
            deliverClaimed(id);
        }
    }

    private List<UUID> claimDueIds() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<PartnerWebhookOutbox> due = outboxRepository.findDueForDelivery(
                    List.of(PartnerWebhookOutboxStatus.PENDING, PartnerWebhookOutboxStatus.FAILED),
                    PartnerWebhookOutboxStatus.DELIVERING,
                    now,
                    now.minusSeconds(Math.max(30, staleDeliveringSeconds)),
                    PageRequest.of(0, Math.max(1, batchSize)));
            for (PartnerWebhookOutbox row : due) {
                row.setStatus(PartnerWebhookOutboxStatus.DELIVERING);
                row.setAttempts(row.getAttempts() == null ? 1 : row.getAttempts() + 1);
                outboxRepository.save(row);
            }
            return due.stream().map(PartnerWebhookOutbox::getId).toList();
        });
    }

    private void deliverClaimed(UUID id) {
        PartnerWebhookOutbox row = outboxRepository.findById(id).orElse(null);
        if (row == null || row.getStatus() != PartnerWebhookOutboxStatus.DELIVERING) {
            return;
        }
        try {
            partnerWebhookClient.post(row.getId().toString(), row.getEventType(), row.getPayloadJson());
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status ->
                    outboxRepository.findById(id).ifPresent(entity -> {
                        entity.setStatus(PartnerWebhookOutboxStatus.DELIVERED);
                        entity.setDeliveredAt(LocalDateTime.now());
                        entity.setLastError(null);
                        outboxRepository.save(entity);
                    }));
            log.info("Partner webhook delivered: id={}, event={}, attempt={}",
                    id, row.getEventType(), row.getAttempts());
        } catch (Exception ex) {
            String error = PartnerWebhookClient.describeFailure(ex);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status ->
                    outboxRepository.findById(id).ifPresent(entity -> {
                        entity.setLastError(error);
                        if (entity.getAttempts() >= maxAttempts) {
                            entity.setStatus(PartnerWebhookOutboxStatus.DEAD);
                        } else {
                            entity.setStatus(PartnerWebhookOutboxStatus.FAILED);
                            entity.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds(entity.getAttempts())));
                        }
                        outboxRepository.save(entity);
                    }));
            log.warn("Partner webhook delivery failed: id={}, event={}, attempt={}, error={}",
                    id, row.getEventType(), row.getAttempts(), error);
        }
    }

    public static long backoffSeconds(int attempts) {
        long seconds = 30L * (1L << Math.min(Math.max(attempts - 1, 0), 10));
        return Math.min(900L, seconds);
    }
}
