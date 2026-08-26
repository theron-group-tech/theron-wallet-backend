package com.theron.wallet.service.impl;

import com.theron.wallet.entity.Account;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.AccountStatus;
import com.theron.wallet.enums.OrganizationStatus;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.AsaasErrorException;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.repository.AccountRepository;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.AccountAsaasGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountAsaasGatewayImpl implements AccountAsaasGateway {

    private static final Set<SubaccountStatus> ALLOWED_STATUSES =
            Set.of(SubaccountStatus.ACTIVE);

    private final AccountRepository accountRepository;
    private final SubaccountRepository subaccountRepository;
    private final AsaasApiKeyResolver asaasApiKeyResolver;

    @Override
    @Transactional(readOnly = true)
    public Subaccount requireConfiguredSubaccount(UUID accountId) {
        Account account = accountRepository.findByIdWithOrganization(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Asaas gate failed: accountId={}, reason=ACCOUNT_NOT_ACTIVE", accountId);
            throw new InvalidRequestException("Account is not ACTIVE");
        }
        if (account.getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
            log.warn("Asaas gate failed: accountId={}, reason=ORGANIZATION_NOT_ACTIVE", accountId);
            throw new InvalidRequestException("Organization is not ACTIVE");
        }

        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId).orElse(null);
        if (subaccount == null) {
            log.warn("Asaas gate failed: accountId={}, reason=NO_SUBACCOUNT — Asaas HTTP will not be called",
                    accountId);
            throw new AsaasErrorException("Account is not linked to an Asaas subaccount");
        }

        if (subaccount.getEncryptedApiKey() == null) {
            log.warn(
                    "Asaas gate failed: accountId={}, subaccountId={}, reason=NO_API_KEY — Asaas HTTP will not be called",
                    accountId,
                    subaccount.getId());
            throw new AsaasErrorException(
                    "Asaas subaccount API key is missing — re-provision the subaccount");
        }

        if (!ALLOWED_STATUSES.contains(subaccount.getStatus())) {
            log.warn(
                    "Asaas gate failed: accountId={}, subaccountId={}, status={} — Asaas HTTP will not be called",
                    accountId,
                    subaccount.getId(),
                    subaccount.getStatus());
            throw new AsaasErrorException(
                    "Asaas subaccount status is " + subaccount.getStatus()
                            + " — complete approval/onboarding before PIX");
        }

        log.debug("Resolved Asaas Subaccount for accountId={}, subaccountId={}",
                accountId, subaccount.getId());
        return subaccount;
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveApiKey(UUID accountId) {
        Subaccount subaccount = requireConfiguredSubaccount(accountId);
        return asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
    }
}
