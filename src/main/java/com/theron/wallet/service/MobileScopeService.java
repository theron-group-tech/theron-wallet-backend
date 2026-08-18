package com.theron.wallet.service;

import com.theron.wallet.entity.Account;

import java.util.List;
import java.util.UUID;

public interface MobileScopeService {

    List<UUID> organizationIds(UUID userId, UUID organizationId);

    List<Account> accounts(UUID userId, UUID organizationId, UUID accountId);

    List<UUID> accountIds(UUID userId, UUID organizationId, UUID accountId);
}
