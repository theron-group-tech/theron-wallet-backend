package com.theron.wallet.service;

import com.theron.wallet.dto.response.AsaasBindResponse;
import com.theron.wallet.entity.Account;

public interface AccountAsaasProvisioningService {

    AsaasBindResponse provision(Account account, String documentOverride);

    AsaasBindResponse provisionByAccountId(java.util.UUID accountId, String documentOverride);

    AsaasBindResponse currentBind(java.util.UUID accountId);

    boolean hasActiveBind(java.util.UUID accountId);
}
