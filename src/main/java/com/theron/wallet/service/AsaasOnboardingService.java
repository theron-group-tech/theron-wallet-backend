package com.theron.wallet.service;

import com.theron.wallet.dto.request.onboarding.B2bAsaasOnboardingSubmitRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAccountTypeRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingAddressRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingBusinessRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingFinancialRequest;
import com.theron.wallet.dto.request.onboarding.OnboardingPersonalRequest;
import com.theron.wallet.dto.response.AccountResponse;
import com.theron.wallet.dto.response.AsaasOnboardingResponse;
import com.theron.wallet.dto.response.AsaasSubaccountStatusResponse;

import java.util.UUID;

public interface AsaasOnboardingService {

    AsaasOnboardingResponse startOrResume(UUID actorUserId);

    AsaasOnboardingResponse getCurrent(UUID actorUserId);

    AsaasOnboardingResponse saveAccountType(UUID actorUserId, OnboardingAccountTypeRequest request);

    AsaasOnboardingResponse savePersonal(UUID actorUserId, OnboardingPersonalRequest request);

    AsaasOnboardingResponse saveBusiness(UUID actorUserId, OnboardingBusinessRequest request);

    AsaasOnboardingResponse saveAddress(UUID actorUserId, OnboardingAddressRequest request);

    AsaasOnboardingResponse saveFinancial(UUID actorUserId, OnboardingFinancialRequest request);

    AsaasOnboardingResponse submit(UUID actorUserId, String idempotencyKey);

    AsaasSubaccountStatusResponse subaccountStatus(UUID actorUserId);

    /** B2B: read onboarding state for a bound Account (no product JWT). */
    AsaasOnboardingResponse getCurrentForAccount(UUID accountId);

    /** B2B: one-shot KYC + Asaas subaccount create for a bound Account. */
    AsaasOnboardingResponse submitOneShotForAccount(
            UUID accountId, B2bAsaasOnboardingSubmitRequest request, String idempotencyKey);

    /** B2B: compact subaccount/onboarding status for a bound Account. */
    AsaasSubaccountStatusResponse subaccountStatusForAccount(UUID accountId);

    boolean isFinancialResourcesEnabled(UUID accountId);

    void enrichAccountResponse(AccountResponse response, UUID accountId);

    void applyAccountStatusWebhook(String asaasAccountId, String eventName);
}
