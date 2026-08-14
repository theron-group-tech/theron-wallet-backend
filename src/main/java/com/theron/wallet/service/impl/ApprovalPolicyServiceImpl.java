package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.CreateApprovalPolicyRequest;
import com.theron.wallet.dto.response.ApprovalPolicyResponse;
import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.ApprovalPolicy;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.ApprovalPolicyStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.ApprovalMapper;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.ApprovalPolicyRepository;
import com.theron.wallet.security.PermissionCodes;
import com.theron.wallet.service.ApprovalPolicyService;
import com.theron.wallet.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalPolicyServiceImpl implements ApprovalPolicyService {

    private final ApprovalPolicyRepository approvalPolicyRepository;
    private final AccountRepository accountRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public ApprovalPolicyResponse create(UUID actorUserId, CreateApprovalPolicyRequest request) {
        Account account = accountRepository.findByIdWithOrganization(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", request.getAccountId()));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidRequestException("Account is not ACTIVE");
        }
        if (account.getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidRequestException("Organization is not ACTIVE");
        }
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_CREATE);

        BigDecimal min = request.getAmountMin();
        BigDecimal max = request.getAmountMax();
        if (max != null && max.compareTo(min) < 0) {
            throw new InvalidRequestException("amountMax must be >= amountMin");
        }
        assertNoOverlap(account.getId(), min, max);

        ApprovalPolicy policy = ApprovalPolicy.builder()
                .account(account)
                .organization(account.getOrganization())
                .amountMin(min)
                .amountMax(max)
                .requiredApprovals(request.getRequiredApprovals())
                .status(ApprovalPolicyStatus.ACTIVE)
                .build();
        policy = approvalPolicyRepository.save(policy);
        log.info("Approval policy created: policyId={}, accountId={}, required={}",
                policy.getId(), account.getId(), policy.getRequiredApprovals());
        return ApprovalMapper.toPolicyResponse(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalPolicyResponse> listByAccount(UUID actorUserId, UUID accountId) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        authorizationService.requirePermission(
                account.getOrganization().getId(), actorUserId, PermissionCodes.APPROVAL_READ);
        return approvalPolicyRepository.findByAccountIdOrderByAmountMinAsc(accountId).stream()
                .map(ApprovalMapper::toPolicyResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int resolveRequiredApprovals(UUID accountId, BigDecimal amount) {
        return approvalPolicyRepository
                .findActiveCoveringAmount(accountId, amount, ApprovalPolicyStatus.ACTIVE)
                .map(ApprovalPolicy::getRequiredApprovals)
                .orElseThrow(() -> new InvalidRequestException(
                        "No ACTIVE approval policy covers amount " + amount + " for this Account"));
    }

    private void assertNoOverlap(UUID accountId, BigDecimal min, BigDecimal max) {
        List<ApprovalPolicy> existing = approvalPolicyRepository.findByAccount_IdAndStatus(
                accountId, ApprovalPolicyStatus.ACTIVE);
        for (ApprovalPolicy policy : existing) {
            BigDecimal otherMin = policy.getAmountMin();
            BigDecimal otherMax = policy.getAmountMax();
            boolean overlaps;
            if (max == null && otherMax == null) {
                overlaps = true;
            } else if (max == null) {
                overlaps = otherMax == null || otherMax.compareTo(min) >= 0;
            } else if (otherMax == null) {
                overlaps = max.compareTo(otherMin) >= 0;
            } else {
                overlaps = min.compareTo(otherMax) <= 0 && otherMin.compareTo(max) <= 0;
            }
            if (overlaps) {
                throw new InvalidRequestException(
                        "Approval policy amount range overlaps an existing ACTIVE policy");
            }
        }
    }
}
