package com.theron.wallet.controller;

import com.theron.wallet.dto.request.ApprovalDecisionRequest;
import com.theron.wallet.dto.request.CreateApprovalPolicyRequest;
import com.theron.wallet.dto.response.ApprovalPolicyResponse;
import com.theron.wallet.dto.response.ApprovalRequestResponse;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.ApprovalPolicyService;
import com.theron.wallet.service.ApprovalWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Financial approval policies and workflow for PIX transfers. Actor from JWT or X-Actor-User-Id.")
public class ApprovalController {

    public static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final ApprovalPolicyService approvalPolicyService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final ActorResolver actorResolver;

    @PostMapping("/policies")
    @Operation(summary = "Create approval policy", description = "Requires approval.create")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Policy created"),
            @ApiResponse(responseCode = "403", description = "Missing permission"),
            @ApiResponse(responseCode = "422", description = "Overlapping range or invalid account")
    })
    public ResponseEntity<ApprovalPolicyResponse> createPolicy(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @Valid @RequestBody CreateApprovalPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(approvalPolicyService.create(actorResolver.requireProductUserId(actorUserId), request));
    }

    @GetMapping("/policies")
    @Operation(summary = "List approval policies by account", description = "Requires approval.read")
    public ResponseEntity<List<ApprovalPolicyResponse>> listPolicies(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam UUID accountId) {
        return ResponseEntity.ok(approvalPolicyService.listByAccount(
                actorResolver.requireProductUserId(actorUserId), accountId));
    }

    @GetMapping
    @Operation(summary = "List approval requests by account", description = "Requires approval.read")
    public ResponseEntity<List<ApprovalRequestResponse>> listRequests(
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestParam UUID accountId) {
        return ResponseEntity.ok(approvalWorkflowService.listByAccount(
                actorResolver.requireProductUserId(actorUserId), accountId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by id", description = "Requires approval.read")
    public ResponseEntity<ApprovalRequestResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId) {
        return ResponseEntity.ok(approvalWorkflowService.getById(
                actorResolver.requireProductUserId(actorUserId), id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve request", description = "Requires approval.approve. Self-approval forbidden.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approved (or partial)"),
            @ApiResponse(responseCode = "403", description = "Missing permission or self-approval"),
            @ApiResponse(responseCode = "409", description = "Duplicate approval by same actor"),
            @ApiResponse(responseCode = "422", description = "Expired or not PENDING")
    })
    public ResponseEntity<ApprovalRequestResponse> approve(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ApprovalDecisionRequest body) {
        ApprovalDecisionRequest decision = mergeDecision(body, idempotencyKey);
        return ResponseEntity.ok(approvalWorkflowService.approve(
                actorResolver.requireProductUserId(actorUserId), id, decision));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject request", description = "Requires approval.reject")
    public ResponseEntity<ApprovalRequestResponse> reject(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ApprovalDecisionRequest body) {
        ApprovalDecisionRequest decision = mergeDecision(body, idempotencyKey);
        return ResponseEntity.ok(approvalWorkflowService.reject(
                actorResolver.requireProductUserId(actorUserId), id, decision));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel request", description = "Requester or approval.create")
    public ResponseEntity<ApprovalRequestResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, required = false) UUID actorUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ApprovalDecisionRequest body) {
        ApprovalDecisionRequest decision = mergeDecision(body, idempotencyKey);
        return ResponseEntity.ok(approvalWorkflowService.cancel(
                actorResolver.requireProductUserId(actorUserId), id, decision));
    }

    private static ApprovalDecisionRequest mergeDecision(ApprovalDecisionRequest body, String headerKey) {
        ApprovalDecisionRequest decision = body != null ? body : new ApprovalDecisionRequest();
        if ((decision.getIdempotencyKey() == null || decision.getIdempotencyKey().isBlank())
                && headerKey != null && !headerKey.isBlank()) {
            decision.setIdempotencyKey(headerKey.trim());
        }
        return decision;
    }
}
