package com.theron.wallet.repository;

import com.theron.wallet.entity.OrganizationMembership;
import com.theron.wallet.enums.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserIdAndStatusIn(
            UUID organizationId, UUID userId, Collection<MembershipStatus> statuses);

    @EntityGraph(attributePaths = {"user", "organization"})
    Page<OrganizationMembership> findByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "organization"})
    Page<OrganizationMembership> findByOrganizationIdAndStatus(
            UUID organizationId, MembershipStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"organization"})
    List<OrganizationMembership> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"organization"})
    List<OrganizationMembership> findByUserIdAndStatus(UUID userId, MembershipStatus status);
}
