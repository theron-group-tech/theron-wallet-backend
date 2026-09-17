package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformPixKey;
import com.theron.wallet.enums.PixKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformPixKeyRepository extends JpaRepository<PlatformPixKey, UUID> {

    Optional<PlatformPixKey> findByKeyAndStatus(String key, PixKeyStatus status);

    List<PlatformPixKey> findByStatus(PixKeyStatus status);

    boolean existsByKeyAndStatus(String key, PixKeyStatus status);
}
