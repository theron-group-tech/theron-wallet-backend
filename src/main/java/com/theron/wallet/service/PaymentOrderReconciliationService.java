package com.theron.wallet.service;

import com.theron.wallet.enums.PaymentOrderStatus;
import com.theron.wallet.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderReconciliationService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentOrderService paymentOrderService;

    /**
     * Fallback reconciliation for providers that do not deliver the final
     * TRANSFER_DONE webhook reliably. Webhooks remain the primary mechanism.
     */
    @Scheduled(fixedDelayString = "${payment-orders.reconciliation.fixed-delay-ms:15000}")
    public void reconcileProcessingOrders() {
        paymentOrderRepository.findTop100ByStatusOrderByCreatedAtAsc(PaymentOrderStatus.PROCESSING)
                .forEach(order -> {
                    try {
                        paymentOrderService.syncProcessingOrder(order.getId());
                    } catch (RuntimeException ex) {
                        log.warn("PaymentOrder reconciliation failed: orderId={}, transferId={}, message={}",
                                order.getId(), order.getAsaasTransferId(), ex.getMessage());
                    }
                });
    }
}
