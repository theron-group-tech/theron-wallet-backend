package com.theron.wallet.repository;

import com.theron.wallet.entity.OauthClient;
import com.theron.wallet.enums.OauthClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OauthClientRepository extends JpaRepository<OauthClient, UUID> {

    Optional<OauthClient> findByClientId(String clientId);

    @Query("""
            SELECT c FROM OauthClient c
            JOIN FETCH c.organization
            WHERE c.id = :id
            """)
    Optional<OauthClient> findByIdWithOrganization(@Param("id") UUID id);

    @Query("""
            SELECT c FROM OauthClient c
            JOIN FETCH c.organization
            WHERE c.clientId = :clientId
            """)
    Optional<OauthClient> findByClientIdWithOrganization(@Param("clientId") String clientId);

    @Query("""
            SELECT DISTINCT c FROM OauthClient c
            LEFT JOIN FETCH c.scopes
            WHERE c.id = :id
            """)
    Optional<OauthClient> findByIdWithScopes(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT c FROM OauthClient c
            LEFT JOIN FETCH c.accounts a
            LEFT JOIN FETCH a.account
            WHERE c.id = :id
            """)
    Optional<OauthClient> findByIdWithAccounts(@Param("id") UUID id);

    List<OauthClient> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId);

    Optional<OauthClient> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByIdAndStatus(UUID id, OauthClientStatus status);
}
