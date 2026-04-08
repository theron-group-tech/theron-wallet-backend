package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateSubaccountRequest;
import com.theron.wallet.dto.response.SubaccountResponse;
import com.theron.wallet.enums.SubaccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubaccountService {

    SubaccountResponse create(CreateSubaccountRequest request);

    SubaccountResponse findById(UUID subaccountId);

    SubaccountResponse findByCpfCnpj(String cpfCnpj);

    /** Paginated list — pass {@code status=null} to return all subaccounts. */
    Page<SubaccountResponse> findAll(SubaccountStatus status, Pageable pageable);
}
