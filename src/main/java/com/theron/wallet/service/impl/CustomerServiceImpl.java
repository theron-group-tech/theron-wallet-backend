package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasCustomerRequest;
import com.theron.wallet.dto.asaas.AsaasCustomerResponse;
import com.theron.wallet.dto.request.CustomerRequest;
import com.theron.wallet.dto.response.CustomerResponse;
import com.theron.wallet.entity.Customer;
import com.theron.wallet.exception.DuplicateResourceException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasCustomerClient;
import com.theron.wallet.mapper.CustomerMapper;
import com.theron.wallet.repository.CustomerRepository;
import com.theron.wallet.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final AsaasCustomerClient asaasCustomerClient;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        log.info("Creating customer with cpfCnpj={}***", maskCpfCnpj(request.getCpfCnpj()));

        if (customerRepository.existsByCpfCnpj(request.getCpfCnpj())) {
            throw new DuplicateResourceException("Customer", "cpfCnpj", request.getCpfCnpj());
        }

        Customer customer = CustomerMapper.toEntity(request);
        customer = customerRepository.save(customer);

        try {
            AsaasCustomerRequest asaasRequest = CustomerMapper.toAsaasRequest(customer);
            AsaasCustomerResponse asaasResponse = asaasCustomerClient.create(asaasRequest);
            customer.setAsaasCustomerId(asaasResponse.getId());
            customer = customerRepository.save(customer);

            log.info("Customer created: id={}, asaasId={}", customer.getId(), asaasResponse.getId());
        } catch (Exception ex) {
            log.error("Failed to create customer in Asaas for id={}: {}", customer.getId(), ex.getMessage());
            throw ex;
        }

        return CustomerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        return CustomerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findByCpfCnpj(String cpfCnpj) {
        Customer customer = customerRepository.findByCpfCnpj(cpfCnpj)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "cpfCnpj", cpfCnpj));
        return CustomerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(CustomerMapper::toResponse);
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        log.info("Updating customer id={}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        CustomerMapper.updateEntity(customer, request);

        if (customer.getAsaasCustomerId() != null) {
            AsaasCustomerRequest asaasRequest = CustomerMapper.toAsaasRequest(customer);
            asaasCustomerClient.update(customer.getAsaasCustomerId(), asaasRequest);
        }

        customer = customerRepository.save(customer);
        log.info("Customer updated: id={}", customer.getId());

        return CustomerMapper.toResponse(customer);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting customer id={}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customerRepository.delete(customer);
        log.info("Customer deleted: id={}", id);
    }

    private String maskCpfCnpj(String cpfCnpj) {
        if (cpfCnpj == null || cpfCnpj.length() < 4) {
            return "****";
        }
        return cpfCnpj.substring(0, cpfCnpj.length() - 4);
    }
}
