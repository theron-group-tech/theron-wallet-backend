package com.theron.wallet.service;

import com.theron.wallet.dto.request.CustomerRequest;
import com.theron.wallet.dto.response.CustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse findById(UUID id);

    CustomerResponse findByCpfCnpj(String cpfCnpj);

    Page<CustomerResponse> findAll(Pageable pageable);

    CustomerResponse update(UUID id, CustomerRequest request);

    void delete(UUID id);
}
