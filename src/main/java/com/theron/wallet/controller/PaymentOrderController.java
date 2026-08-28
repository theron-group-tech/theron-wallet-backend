package com.theron.wallet.controller;

import com.theron.wallet.dto.request.CreatePaymentOrderRequest;
import com.theron.wallet.dto.request.DecidePaymentOrderRequest;
import com.theron.wallet.dto.response.PaymentOrderDestinationResponse;
import com.theron.wallet.dto.response.PaymentOrderResponse;
import com.theron.wallet.enums.PaymentOrderStatus;
import com.theron.wallet.security.ActorResolver;
import com.theron.wallet.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-orders")
@RequiredArgsConstructor
@Tag(name = "Payment Orders", description = "Administrative payment orders (FINANCE creates, OWNER approves)")
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;
    private final ActorResolver actorResolver;

    @PostMapping
    @Operation(summary = "Create payment order", description = "Does not debit. Source is always the OWNER account.")
    public ResponseEntity<PaymentOrderResponse> create(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentOrderService.create(actorResolver.requireProductUserId(), request));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentOrderResponse>> list(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) PaymentOrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentOrderService.list(
                actorResolver.requireProductUserId(), organizationId, status, pageable));
    }

    @GetMapping("/destinations")
    @Operation(summary = "List destination accounts for payment orders (no balances)")
    public ResponseEntity<List<PaymentOrderDestinationResponse>> destinations(
            @RequestParam UUID organizationId) {
        return ResponseEntity.ok(paymentOrderService.listDestinations(
                actorResolver.requireProductUserId(), organizationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentOrderService.get(actorResolver.requireProductUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentOrderResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentOrderService.cancel(actorResolver.requireProductUserId(), id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentOrderResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) DecidePaymentOrderRequest request) {
        return ResponseEntity.ok(paymentOrderService.approve(
                actorResolver.requireProductUserId(), id, request == null ? new DecidePaymentOrderRequest() : request));
    }

    @PostMapping("/{id}/sync")
    @Operation(summary = "Synchronize a PROCESSING payment order with Asaas")
    public ResponseEntity<PaymentOrderResponse> sync(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentOrderService.syncProcessingOrder(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PaymentOrderResponse> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) DecidePaymentOrderRequest request) {
        return ResponseEntity.ok(paymentOrderService.reject(
                actorResolver.requireProductUserId(), id, request == null ? new DecidePaymentOrderRequest() : request));
    }
}
