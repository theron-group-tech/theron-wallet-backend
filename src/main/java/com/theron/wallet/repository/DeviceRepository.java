package com.theron.wallet.repository;

import com.theron.wallet.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByUserIdAndClientDeviceId(UUID userId, String clientDeviceId);
}
