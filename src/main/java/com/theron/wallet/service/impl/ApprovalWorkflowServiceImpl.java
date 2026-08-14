package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.ApprovalDecisionRequest;
import com.theron.wallet.dto.response.ApprovalRequestResponse;
import com.theron.wallet.entity.ApprovalAction;
import com.theron.wallet.entity.ApprovalRequest;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.ApprovalActionType;
import com.theron.wallet.enums.ApprovalRequestStatus;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.ApprovalMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ApprovalActionRepository;
import com.theron.wallet.repository.ApprovalRequestRepository;
import com.theron.wallet.repository.PixTransactionRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.ApprovalWorkflowService;
import com.theron.wallet.service.AuthorizationService;
import com.theron.wallet.service.PixService;
import com.theron.wallet.service.TransactionLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    public static final int DEFAULT_EXPIRY_HOURS = 72;

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final PixTransactionRepository pixTransactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AuthorizationService authorizationService;
    private final TransactionLifecycleService transactionLifecycleService;
    private final PlatformTransactionManager transactionManager;
    private final PixService pixService;

    public ApprovalWorkflowServiceImpl(
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalActionRepository approvalActionRepository,
            PixTransactionRepository pixTransactionRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            AuthorizationService authorizationService,
            TransactionLifecycleService transactionLifecycleService,
            PlatformTransactionManager transactionManager,
            @Lazy PixService pixService) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.pixTransactionRepository = pixTransactionRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.authorizationService = authorizationService;
        this.transactionLifecycleService = transactionLifecycleService;
        this.transactionManager = transactionManager;
        this.pixService = pixService;
    }

    @Override
    @Transactional
    public ApprovalRequest createPendingRequest(
            Transaction transaction,
            User requestedBy,
            int requiredApprovals) {
        if (requiredApprovals < 1) {
            throw new InvalidRequestException("requiredApprovals must be >= 1 for pending request");
        }
        ApprovalRequest request = ApprovalRequest.builder()
                .transaction(transaction)
                .account(transaction.getAccount())
                .organization(transaction.getOrganization())
                .requestedBy(requestedBy)
                .requiredApprovals(requiredApprovals)
                .approvedCount(0)
                .status(ApprovalRequestStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS))
                .build();
        return approvalRequestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalRequestResponse getById(UUID actorUserId, UUID approvalRequestId) {
        ApprovalRequest request = loadWithDetails(approvalRequestId);
        authorizationService.requirePermission(
                request.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_READ);
        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> listByAccount(UUID actorUserId, UUID accountId) {
        var account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_READ);
        return approvalRequestRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ApprovalRequestResponse approve(
            UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest decision) {
        expireIfNeeded(approvalRequestId);

        ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", approvalRequestId));
        initializeLockedRequest(request);

        authorizationService.requirePermission(
                request.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_APPROVE);
        ensureNotExpired(request);
        ensurePending(request);

        if (request.getRequestedBy().getId().equals(actorUserId)) {
            throw new ForbiddenException("Requester cannot approve their own transaction");
        }

        if (decision != null && decision.getIdempotencyKey() != null && !decision.getIdempotencyKey().isBlank()) {
            var existing = approvalActionRepository.findByIdempotencyKey(decision.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return toResponse(loadWithDetails(approvalRequestId));
            }
        }

        if (approvalActionRepository.existsByApprovalRequest_IdAndActor_IdAndAction(
                request.getId(), actorUserId, ApprovalActionType.APPROVE)) {
            throw new DuplicateResourceException("Actor already approved this request");
        }

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        saveAction(request, actor, ApprovalActionType.APPROVE, decision);

        request.setApprovedCount(request.getApprovedCount() + 1);
        boolean readyToExecute = request.getApprovedCount() >= request.getRequiredApprovals();
        if (readyToExecute) {
            request.setStatus(ApprovalRequestStatus.APPROVED);
        }
        approvalRequestRepository.save(request);

        if (readyToExecute) {
            pixService.executeApprovedTransfer(request.getTransaction().getId());
        }

        log.info("Approval APPROVE: requestId={}, count={}/{}",
                request.getId(), request.getApprovedCount(), request.getRequiredApprovals());
        return toResponse(loadWithDetails(request.getId()));
    }

    @Override
    @Transactional
    public ApprovalRequestResponse reject(
            UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest decision) {
        expireIfNeeded(approvalRequestId);

        ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", approvalRequestId));
        initializeLockedRequest(request);

        authorizationService.requirePermission(
                request.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_REJECT);
        ensureNotExpired(request);
        ensurePending(request);

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        saveAction(request, actor, ApprovalActionType.REJECT, decision);
        request.setStatus(ApprovalRequestStatus.REJECTED);
        approvalRequestRepository.save(request);
        cancelHeldTransaction(request.getTransaction());

        log.info("Approval REJECT: requestId={}", request.getId());
        return toResponse(loadWithDetails(request.getId()));
    }

    @Override
    @Transactional
    public ApprovalRequestResponse cancel(
            UUID actorUserId, UUID approvalRequestId, ApprovalDecisionRequest decision) {
        expireIfNeeded(approvalRequestId);

        ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", approvalRequestId));
        initializeLockedRequest(request);

        boolean isRequester = request.getRequestedBy().getId().equals(actorUserId);
        if (!isRequester) {
            authorizationService.requirePermission(
                    request.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_CREATE);
        }
        ensureNotExpired(request);
        ensurePending(request);

        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorUserId));
        saveAction(request, actor, ApprovalActionType.CANCEL, decision);
        request.setStatus(ApprovalRequestStatus.CANCELLED);
        approvalRequestRepository.save(request);
        cancelHeldTransaction(request.getTransaction());

        log.info("Approval CANCEL: requestId={}", request.getId());
        return toResponse(loadWithDetails(request.getId()));
    }

    private void initializeLockedRequest(ApprovalRequest request) {
        request.getOrganization().getId();
        request.getRequestedBy().getId();
        request.getTransaction().getId();
        request.getAccount().getId();
    }

    private void ensurePending(ApprovalRequest request) {
        if (request.getStatus() != ApprovalRequestStatus.PENDING) {
            throw new InvalidRequestException("Approval request is not PENDING: " + request.getStatus());
        }
    }

    /**
     * Lazy expiry before acquiring the main FOR UPDATE lock (avoids self-deadlock with REQUIRES_NEW).
     */
    private void expireIfNeeded(UUID approvalRequestId) {
        ApprovalRequest preview = approvalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", approvalRequestId));
        if (!preview.getExpiresAt().isBefore(LocalDateTime.now())) {
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            ApprovalRequest locked = approvalRequestRepository.findByIdForUpdate(approvalRequestId)
                    .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", approvalRequestId));
            if (locked.getStatus() == ApprovalRequestStatus.PENDING
                    && locked.getExpiresAt().isBefore(LocalDateTime.now())) {
                locked.setStatus(ApprovalRequestStatus.EXPIRED);
                approvalRequestRepository.save(locked);
                cancelHeldTransaction(locked.getTransaction());
            }
        });
        throw new InvalidRequestException("Approval request has expired");
    }

    private void ensureNotExpired(ApprovalRequest request) {
        if (request.getExpiresAt().isBefore(LocalDateTime.now())
                || request.getStatus() == ApprovalRequestStatus.EXPIRED) {
            throw new InvalidRequestException("Approval request has expired");
        }
    }

    private void cancelHeldTransaction(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.PENDING_APPROVAL) {
            transactionLifecycleService.transition(transaction, TransactionStatus.CANCELLED);
            pixTransactionRepository.findByTransactionIdForUpdate(transaction.getId()).ifPresent(pt -> {
                pt.setStatus(TransactionStatus.CANCELLED);
                pixTransactionRepository.save(pt);
            });
        }
    }

    private void saveAction(
            ApprovalRequest request,
            User actor,
            ApprovalActionType type,
            ApprovalDecisionRequest decision) {
        ApprovalAction action = ApprovalAction.builder()
                .approvalRequest(request)
                .actor(actor)
                .action(type)
                .comment(decision != null ? decision.getComment() : null)
                .idempotencyKey(decision != null && decision.getIdempotencyKey() != null
                        && !decision.getIdempotencyKey().isBlank()
                        ? decision.getIdempotencyKey().trim()
                        : null)
                .build();
        try {
            approvalActionRepository.saveAndFlush(action);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Duplicate approval action");
        }
    }

    private ApprovalRequest loadWithDetails(UUID id) {
        return approvalRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", id));
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest request) {
        List<ApprovalAction> actions = approvalActionRepository
                .findByApprovalRequest_IdOrderByCreatedAtAsc(request.getId());
        return ApprovalMapper.toRequestResponse(request, actions);
    }
}
