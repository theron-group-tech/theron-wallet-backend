package com.theron.wallet.service;

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

    boolean isFinancialResourcesEnabled(UUID accountId);

    void enrichAccountResponse(AccountResponse response, UUID accountId);

    void applyAccountStatusWebhook(String asaasAccountId, String eventName);
}
