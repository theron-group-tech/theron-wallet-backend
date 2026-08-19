package com.theron.wallet.repository;

import com.theron.wallet.entity.Organization;
import com.theron.wallet.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {

    boolean existsByDocument(String document);

    Optional<Organization> findByDocument(String document);

    Page<Organization> findByStatus(OrganizationStatus status, Pageable pageable);
}
