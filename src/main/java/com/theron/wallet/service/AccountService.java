package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreateAccountRequest;
import com.theron.wallet.dto.request.UpdateAccountRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.WalletResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    AccountResponse create(UUID organizationId, CreateAccountRequest request);

    List<AccountResponse> listByOrganization(UUID organizationId);

    AccountResponse findById(UUID id);

    AccountResponse update(UUID id, UpdateAccountRequest request);

    WalletResponse findWallet(UUID accountId);
}
