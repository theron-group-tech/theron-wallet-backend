package com.theron.wallet.repository;

import com.theron.wallet.entity.BillingCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, UUID> {

    Optional<BillingCustomer> findByAccount_IdAndCpfCnpj(UUID accountId, String cpfCnpj);
}
