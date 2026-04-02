package com.theron.wallet.repository;

import com.theron.wallet.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByCpfCnpj(String cpfCnpj);

    Optional<Customer> findByAsaasCustomerId(String asaasCustomerId);

    boolean existsByCpfCnpj(String cpfCnpj);

    boolean existsByEmail(String email);
}
