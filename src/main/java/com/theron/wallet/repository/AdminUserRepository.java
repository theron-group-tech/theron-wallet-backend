package com.theron.wallet.repository;

import com.theron.wallet.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);
}

