package com.theron.wallet.repository;

import com.theron.wallet.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId);

    @Query("""
            SELECT a FROM Account a
            JOIN FETCH a.organization
            WHERE a.id = :id
            """)
    Optional<Account> findByIdWithOrganization(@Param("id") UUID id);
}
