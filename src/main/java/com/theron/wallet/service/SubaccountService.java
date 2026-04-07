package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;

import java.util.UUID;

public interface SubaccountService {

    SubaccountResponse create(CreateSubaccountRequest request);

    SubaccountResponse findById(UUID subaccountId);

    SubaccountResponse findByCpfCnpj(String cpfCnpj);
}
