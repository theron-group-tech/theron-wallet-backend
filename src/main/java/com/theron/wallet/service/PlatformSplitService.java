package com.theron.wallet.service;

import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.SplitConfigResponse;

import java.util.UUID;

public interface PlatformSplitService {

    SplitConfigResponse getConfig();

    SplitConfigResponse update(UpdateSplitConfigRequest request, UUID adminId);

    /**
     * Legacy entry: applies global {@code PlatformSplitConfig} (no org-specific residual rule).
     */
    void applyToPayment(AsaasPaymentRequest paymentRequest);

    /**
     * Applies platform split. For Vem Comigo with counterparty splits, Theron fee is
     * {@code residual × commissionPercent} as fixedValue; otherwise uses PlatformSplitConfig.
     */
    void applyToPayment(AsaasPaymentRequest paymentRequest, UUID organizationId);
}
