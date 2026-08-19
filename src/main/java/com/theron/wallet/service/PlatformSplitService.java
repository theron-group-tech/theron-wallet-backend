package com.theron.wallet.service;

import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.SplitConfigResponse;

public interface PlatformSplitService {

    SplitConfigResponse getConfig();

    SplitConfigResponse update(UpdateSplitConfigRequest request, java.util.UUID adminId);

    void applyToPayment(AsaasPaymentRequest paymentRequest);
}
