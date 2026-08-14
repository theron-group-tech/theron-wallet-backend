package com.theron.wallet.repository;

import com.theron.wallet.entity.PixKey;
import com.theron.wallet.enums.PixKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

    @Query("""
            SELECT p FROM PixKey p
            JOIN FETCH p.account
            JOIN FETCH p.organization
            WHERE p.account.id = :accountId
            ORDER BY p.createdAt DESC
            """)
    List<PixKey> findByAccountIdOrderByCreatedAtDesc(@Param("accountId") UUID accountId);

    @Query("""
            SELECT p FROM PixKey p
            JOIN FETCH p.account a
            JOIN FETCH a.organization
            JOIN FETCH p.organization
            WHERE p.id = :id
            """)
    Optional<PixKey> findByIdWithOwner(@Param("id") UUID id);

    boolean existsByAccount_IdAndKeyAndStatus(UUID accountId, String key, PixKeyStatus status);

    boolean existsByAccount_IdAndKeyAndStatusAndIdNot(UUID accountId, String key, PixKeyStatus status, UUID id);
}
