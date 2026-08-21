package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Short> {
}
