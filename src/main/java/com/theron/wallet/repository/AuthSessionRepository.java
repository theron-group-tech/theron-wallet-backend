package com.theron.wallet.repository;

import com.theron.wallet.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AuthSession s WHERE s.refreshTokenHash = :hash")
    Optional<AuthSession> findByRefreshTokenHashForUpdate(@Param("hash") String hash);

    @Query("SELECT s FROM AuthSession s JOIN FETCH s.user WHERE s.id = :id")
    Optional<AuthSession> findWithUserById(@Param("id") UUID id);

    Optional<AuthSession> findByIdAndUser_Id(UUID id, UUID userId);

    List<AuthSession> findByUser_IdAndRevokedAtIsNullAndReplacedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<AuthSession> findByUser_IdAndRevokedAtIsNull(UUID userId);

    long countByDevice_IdAndRevokedAtIsNullAndReplacedAtIsNull(UUID deviceId);

    long countByUser_IdAndDevice_IdAndRevokedAtIsNullAndReplacedAtIsNull(UUID userId, UUID deviceId);
}
