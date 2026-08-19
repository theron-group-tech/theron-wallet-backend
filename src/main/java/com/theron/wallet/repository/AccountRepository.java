package com.theron.wallet.repository;

import com.theron.wallet.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId);

    List<Account> findByOrganization_IdInOrderByCreatedAtDesc(Collection<UUID> organizationIds);

    Page<Account> findByOrganization_IdIn(Collection<UUID> organizationIds, Pageable pageable);

    @Query("""
            SELECT a FROM Account a
            JOIN FETCH a.organization
            LEFT JOIN FETCH a.ownerUser
            WHERE a.id = :id
            """)
    Optional<Account> findByIdWithOrganization(@Param("id") UUID id);

    Optional<Account> findByOrganization_IdAndOwnerUser_Id(UUID organizationId, UUID ownerUserId);

    boolean existsByOrganization_IdAndOwnerUser_Id(UUID organizationId, UUID ownerUserId);

    List<Account> findByOrganization_IdAndOwnerUser_IdOrderByCreatedAtDesc(UUID organizationId, UUID ownerUserId);
}
