package com.theron.wallet.repository;

import com.theron.wallet.entity.ApprovalAction;
import com.theron.wallet.enums.ApprovalActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {

    List<ApprovalAction> findByApprovalRequest_IdOrderByCreatedAtAsc(UUID approvalRequestId);

    boolean existsByApprovalRequest_IdAndActor_IdAndAction(
            UUID approvalRequestId, UUID actorUserId, ApprovalActionType action);

    Optional<ApprovalAction> findByIdempotencyKey(String idempotencyKey);
}
