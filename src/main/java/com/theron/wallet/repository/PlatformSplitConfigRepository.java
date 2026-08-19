package com.theron.wallet.repository;

import com.theron.wallet.entity.PlatformSplitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformSplitConfigRepository extends JpaRepository<PlatformSplitConfig, Short> {
}
