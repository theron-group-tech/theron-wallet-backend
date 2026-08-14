package com.theron.wallet.repository;

import com.theron.wallet.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @Query("""
            SELECT b FROM Beneficiary b
            JOIN FETCH b.organization
            JOIN FETCH b.createdBy
            WHERE b.organization.id = :organizationId
            ORDER BY b.createdAt DESC
            """)
    List<Beneficiary> findByOrganizationIdOrderByCreatedAtDesc(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT b FROM Beneficiary b
            JOIN FETCH b.organization
            JOIN FETCH b.createdBy
            WHERE b.id = :id
            """)
    Optional<Beneficiary> findByIdWithOwner(@Param("id") UUID id);

    boolean existsByOrganization_IdAndPixKey(UUID organizationId, String pixKey);

    boolean existsByOrganization_IdAndPixKeyAndIdNot(UUID organizationId, String pixKey, UUID id);

    boolean existsByOrganization_IdAndBankCodeAndBranchAndAccountNumber(
            UUID organizationId, String bankCode, String branch, String accountNumber);

    boolean existsByOrganization_IdAndBankCodeAndBranchAndAccountNumberAndIdNot(
            UUID organizationId, String bankCode, String branch, String accountNumber, UUID id);
}
