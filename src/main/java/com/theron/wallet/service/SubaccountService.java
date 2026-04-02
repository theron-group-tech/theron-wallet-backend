package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;

import java.util.UUID;

public interface SubaccountService {

    SubaccountResponse create(CreateSubaccountRequest request);

    SubaccountResponse findById(UUID subaccountId);

    SubaccountResponse findByCustomerId(UUID customerId);

    /**
     * Asserts that the subaccount linked to the given customer is in a status
     * that allows outbound operations (charge creation, transfers, etc.).
     * Throws SubaccountOperationBlockedException if blocked.
     */
    void assertOutboundOperationsAllowed(UUID customerId);
}
