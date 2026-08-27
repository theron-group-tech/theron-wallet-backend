package com.theron.wallet.controller.admin;

import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payment-orders")
@RequiredArgsConstructor
@Tag(name = "Platform Admin Payment Orders", description = "Admin operations for stuck payment orders")
public class AdminPaymentOrderController {

    private final PaymentOrderService paymentOrderService;

    @PostMapping("/{id}/sync")
    @Operation(summary = "Sync PROCESSING payment order against Asaas transfer status")
    public ResponseEntity<PaymentOrderResponse> sync(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentOrderService.syncProcessingOrder(id));
    }
}
