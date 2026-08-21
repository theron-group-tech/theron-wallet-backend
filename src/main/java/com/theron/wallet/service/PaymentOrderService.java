package com.theron.wallet.service;

import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.DecidePaymentOrderRequest;
import com.theron.wallet.dto.response.PaymentOrderDestinationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.enums.PaymentOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentOrderService {

    PaymentOrderResponse create(UUID actorUserId, CreatePaymentOrderRequest request);

    Page<PaymentOrderResponse> list(UUID actorUserId, UUID organizationId, PaymentOrderStatus status, Pageable pageable);

    PaymentOrderResponse get(UUID actorUserId, UUID paymentOrderId);

    PaymentOrderResponse cancel(UUID actorUserId, UUID paymentOrderId);

    PaymentOrderResponse approve(UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request);

    PaymentOrderResponse reject(UUID actorUserId, UUID paymentOrderId, DecidePaymentOrderRequest request);

    List<PaymentOrderDestinationResponse> listDestinations(UUID actorUserId, UUID organizationId);
}
