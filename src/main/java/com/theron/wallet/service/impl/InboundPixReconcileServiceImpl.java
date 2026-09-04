package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPaymentResponse;
import com.theron.wallet.dto.response.InboundReconcileResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.entity.Transaction;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasPaymentClient;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.repository.TransactionRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.service.InboundPixCreditService;
import com.theron.wallet.service.InboundPixReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundPixReconcileServiceImpl implements InboundPixReconcileService {

    private static final Set<String> CREDITABLE = Set.of("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;

    private final SubaccountRepository subaccountRepository;
    private final TransactionRepository transactionRepository;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final AsaasPaymentClient asaasPaymentClient;
    private final InboundPixCreditService inboundPixCreditService;
    private final PlatformPixInboundDedupe platformPixInboundDedupe;

    @Override
    @Transactional
    public InboundReconcileResponse reconcileAccount(UUID accountId) {
        Subaccount subaccount = subaccountRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "accountId", accountId));
        if (!subaccount.getStatus().allowsInboundProcessing()) {
            throw new InvalidRequestException(
                    "Subaccount status does not allow inbound reconcile: " + subaccount.getStatus());
        }
        if (subaccount.getEncryptedApiKey() == null || subaccount.getEncryptedApiKey().length == 0) {
            throw new InvalidRequestException("Subaccount has no Asaas API key for reconcile");
        }

        String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
        List<InboundReconcileResponse.Item> items = new ArrayList<>();
        int scanned = 0;
        int credited = 0;
        int skipped = 0;

        for (String status : List.of("RECEIVED", "CONFIRMED")) {
            int offset = 0;
            for (int page = 0; page < MAX_PAGES; page++) {
                AsaasListResponse<AsaasPaymentResponse> list =
                        asaasPaymentClient.listPayments(apiKey, status, offset, PAGE_SIZE);
                List<AsaasPaymentResponse> data = list != null && list.getData() != null
                        ? list.getData()
                        : List.of();
                if (data.isEmpty()) {
                    break;
                }
                for (AsaasPaymentResponse payment : data) {
                    scanned++;
                    InboundReconcileResponse.Item item = creditPayment(subaccount, payment);
                    items.add(item);
                    if ("CREDITED".equals(item.getStatus())) {
                        credited++;
                    } else {
                        skipped++;
                    }
                }
                offset += data.size();
                if (Boolean.FALSE.equals(list.getHasMore()) || data.size() < PAGE_SIZE) {
                    break;
                }
            }
        }

        log.info("Inbound PIX reconcile finished: accountId={}, scanned={}, credited={}, skipped={}",
                accountId, scanned, credited, skipped);
        return InboundReconcileResponse.builder()
                .accountId(accountId)
                .subaccountId(subaccount.getId())
                .scanned(scanned)
                .credited(credited)
                .skipped(skipped)
                .items(items)
                .build();
    }

    private InboundReconcileResponse.Item creditPayment(Subaccount subaccount, AsaasPaymentResponse payment) {
        if (payment == null || payment.getId() == null || payment.getId().isBlank()) {
            return InboundReconcileResponse.Item.builder()
                    .status("SKIPPED")
                    .message("Payment without id")
                    .build();
        }
        String remoteStatus = payment.getStatus() != null
                ? payment.getStatus().trim().toUpperCase(Locale.ROOT)
                : "";
        if (!CREDITABLE.contains(remoteStatus)) {
            return InboundReconcileResponse.Item.builder()
                    .asaasPaymentId(payment.getId())
                    .amount(payment.getValue())
                    .status("SKIPPED")
                    .message("Status not creditable: " + payment.getStatus())
                    .build();
        }
        BigDecimal amount = payment.getValue() != null ? payment.getValue() : payment.getNetValue();
        if (amount == null || amount.signum() <= 0) {
            return InboundReconcileResponse.Item.builder()
                    .asaasPaymentId(payment.getId())
                    .status("SKIPPED")
                    .message("Non-positive amount")
                    .build();
        }

        boolean already = transactionRepository.findByAsaasPaymentId(payment.getId()).isPresent()
                || transactionRepository.findByIdempotencyKey(
                        InboundPixCreditService.idempotencyKey(payment.getId())).isPresent();

        if (platformPixInboundDedupe.alreadyCreditedByPlatform(
                subaccount, amount, null, payment.getDescription())) {
            return InboundReconcileResponse.Item.builder()
                    .asaasPaymentId(payment.getId())
                    .amount(amount)
                    .status("SKIPPED")
                    .message("Already credited via platform_pix_transfer")
                    .build();
        }

        Optional<Transaction> credited = inboundPixCreditService.credit(
                subaccount,
                payment.getId(),
                amount,
                payment.getDescription(),
                payment.getExternalReference());

        if (credited.isEmpty()) {
            return InboundReconcileResponse.Item.builder()
                    .asaasPaymentId(payment.getId())
                    .amount(amount)
                    .status("SKIPPED")
                    .message("Credit skipped")
                    .build();
        }

        return InboundReconcileResponse.Item.builder()
                .asaasPaymentId(payment.getId())
                .amount(amount)
                .status(already ? "ALREADY_CREDITED" : "CREDITED")
                .transactionId(credited.get().getId())
                .build();
    }
}
