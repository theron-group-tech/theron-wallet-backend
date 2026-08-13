package com.theron.wallet.service.impl;

import com.theron.wallet.dto.request.AddOrganizationMemberRequest;
import com.theron.wallet.dto.request.UpdateOrganizationMemberRequest;
import com.theron.wallet.dto.response.OrganizationMembershipResponse;
import com.theron.wallet.entity.Organization;
import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.entity.User;
import com.theron.wallet.enums.MembershipStatus;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ForbiddenException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.mapper.UserMapper;
import com.theron.wallet.repository.OrganizationMembershipRepository;
import com.theron.wallet.repository.OrganizationRepository;
import com.theron.wallet.repository.UserRepository;
import com.theron.wallet.service.OrganizationMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationMembershipServiceImpl implements OrganizationMembershipService {

    private static final Set<MembershipStatus> ACCESS_GRANTING_STATUSES =
            EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.INVITED);

    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public OrganizationMembershipResponse addMember(UUID organizationId, AddOrganizationMemberRequest request) {
        Organization organization = getOrganizationOrThrow(organizationId);
        User user = getUserOrThrow(request.getUserId());

        MembershipStatus targetStatus = request.getStatus() != null
                ? request.getStatus()
                : MembershipStatus.ACTIVE;

        if (targetStatus == MembershipStatus.REMOVED) {
            throw new InvalidRequestException("Cannot add member with status REMOVED; use remove endpoint");
        }

        return membershipRepository.findByOrganizationIdAndUserId(organizationId, user.getId())
                .map(existing -> reactivateOrReject(existing, targetStatus))
                .orElseGet(() -> {
                    OrganizationMembership membership = OrganizationMembership.builder()
                            .organization(organization)
                            .user(user)
                            .status(targetStatus)
                            .build();
                    membership = membershipRepository.save(membership);
                    log.info("Membership created: organizationId={}, userId={}, status={}",
                            organizationId, user.getId(), targetStatus);
                    return UserMapper.toMembershipResponse(membership);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationMembershipResponse> listMembers(
            UUID organizationId, MembershipStatus status, Pageable pageable) {
        getOrganizationOrThrow(organizationId);
        Page<OrganizationMembership> page = status != null
                ? membershipRepository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                : membershipRepository.findByOrganizationId(organizationId, pageable);
        return page.map(UserMapper::toMembershipResponse);
    }

    @Override
    @Transactional
    public OrganizationMembershipResponse updateMember(
            UUID organizationId, UUID userId, UpdateOrganizationMemberRequest request) {
        getOrganizationOrThrow(organizationId);
        getUserOrThrow(userId);

        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OrganizationMembership", "organizationId+userId",
                        organizationId + "/" + userId));

        membership.setStatus(request.getStatus());
        membership = membershipRepository.save(membership);
        log.info("Membership updated: organizationId={}, userId={}, status={}",
                organizationId, userId, request.getStatus());
        return UserMapper.toMembershipResponse(membership);
    }

    @Override
    @Transactional
    public OrganizationMembershipResponse removeMember(UUID organizationId, UUID userId) {
        getOrganizationOrThrow(organizationId);
        getUserOrThrow(userId);

        OrganizationMembership membership = membershipRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OrganizationMembership", "organizationId+userId",
                        organizationId + "/" + userId));

        membership.setStatus(MembershipStatus.REMOVED);
        membership = membershipRepository.save(membership);
        log.info("Membership removed: organizationId={}, userId={}", organizationId, userId);
        return UserMapper.toMembershipResponse(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertActiveMembership(UUID organizationId, UUID userId) {
        boolean hasAccess = membershipRepository.existsByOrganizationIdAndUserIdAndStatusIn(
                organizationId, userId, Set.of(MembershipStatus.ACTIVE));
        if (!hasAccess) {
            throw new ForbiddenException(
                    "User does not have active membership in organization " + organizationId);
        }
    }

    private OrganizationMembershipResponse reactivateOrReject(
            OrganizationMembership existing, MembershipStatus targetStatus) {
        if (ACCESS_GRANTING_STATUSES.contains(existing.getStatus())) {
            throw new DuplicateResourceException(
                    "OrganizationMembership", "organizationId+userId",
                    existing.getOrganization().getId() + "/" + existing.getUser().getId());
        }
        // REMOVED or SUSPENDED → reactivate / update
        existing.setStatus(targetStatus);
        existing = membershipRepository.save(existing);
        log.info("Membership reactivated: organizationId={}, userId={}, status={}",
                existing.getOrganization().getId(), existing.getUser().getId(), targetStatus);
        return UserMapper.toMembershipResponse(existing);
    }

    private Organization getOrganizationOrThrow(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", "id", organizationId));
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
