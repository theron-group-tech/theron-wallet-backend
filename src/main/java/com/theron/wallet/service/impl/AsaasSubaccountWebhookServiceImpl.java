package com.theron.wallet.service.impl;

import com.theron.wallet.dto.asaas.AsaasWebhookConfigRequest;
import com.theron.wallet.dto.asaas.AsaasWebhookConfigResponse;
import com.theron.wallet.dto.response.SubaccountWebhookRepairResponse;
import com.theron.wallet.entity.Subaccount;
import com.theron.wallet.enums.SubaccountStatus;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.exception.ResourceNotFoundException;
import com.theron.wallet.integration.AsaasWebhookClient;
import com.theron.wallet.integration.AsaasWebhookConfigFactory;
import com.theron.wallet.repository.SubaccountRepository;
import com.theron.wallet.security.AsaasApiKeyResolver;
import com.theron.wallet.security.WebhookTokenGenerator;
import com.theron.wallet.service.AsaasSubaccountWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasSubaccountWebhookServiceImpl implements AsaasSubaccountWebhookService {

    private final SubaccountRepository subaccountRepository;
    private final AsaasWebhookConfigFactory webhookConfigFactory;
    private final AsaasWebhookClient asaasWebhookClient;
    private final AsaasApiKeyResolver asaasApiKeyResolver;
    private final WebhookTokenGenerator webhookTokenGenerator;

    @Override
    @Transactional
    public SubaccountWebhookRepairResponse repairForSubaccount(UUID subaccountId) {
        Subaccount subaccount = subaccountRepository.findById(subaccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subaccount", "id", subaccountId));
        SubaccountWebhookRepairResponse.Item item = repairOne(subaccount);
        int succeeded = "SUCCEEDED".equals(item.getStatus()) ? 1 : 0;
        int skipped = "SKIPPED".equals(item.getStatus()) ? 1 : 0;
        int failed = "FAILED".equals(item.getStatus()) ? 1 : 0;
        return SubaccountWebhookRepairResponse.builder()
                .attempted(1)
                .succeeded(succeeded)
                .skipped(skipped)
                .failed(failed)
                .subaccountId(subaccountId)
                .asaasWebhookId(item.getAsaasWebhookId())
                .items(List.of(item))
                .build();
    }

    @Override
    @Transactional
    public SubaccountWebhookRepairResponse repairAllActive() {
        List<Subaccount> subaccounts = subaccountRepository.findByStatus(SubaccountStatus.ACTIVE);
        List<SubaccountWebhookRepairResponse.Item> items = new ArrayList<>();
        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        for (Subaccount subaccount : subaccounts) {
            SubaccountWebhookRepairResponse.Item item = repairOne(subaccount);
            items.add(item);
            switch (item.getStatus()) {
                case "SUCCEEDED" -> succeeded++;
                case "SKIPPED" -> skipped++;
                default -> failed++;
            }
        }
        return SubaccountWebhookRepairResponse.builder()
                .attempted(subaccounts.size())
                .succeeded(succeeded)
                .skipped(skipped)
                .failed(failed)
                .items(items)
                .build();
    }

    private SubaccountWebhookRepairResponse.Item repairOne(Subaccount subaccount) {
        UUID accountId = subaccount.getAccount() != null ? subaccount.getAccount().getId() : null;
        if (!webhookConfigFactory.isWebhookUrlConfigured()) {
            return SubaccountWebhookRepairResponse.Item.builder()
                    .subaccountId(subaccount.getId())
                    .accountId(accountId)
                    .status("SKIPPED")
                    .message("ASAAS_WEBHOOK_URL is not configured")
                    .build();
        }
        if (subaccount.getEncryptedApiKey() == null || subaccount.getEncryptedApiKey().length == 0) {
            return SubaccountWebhookRepairResponse.Item.builder()
                    .subaccountId(subaccount.getId())
                    .accountId(accountId)
                    .status("SKIPPED")
                    .message("Subaccount has no Asaas API key")
                    .build();
        }

        if (subaccount.getWebhookToken() == null || subaccount.getWebhookToken().isBlank()) {
            subaccount.setWebhookToken(webhookTokenGenerator.generate());
            subaccountRepository.save(subaccount);
        }

        AsaasWebhookConfigRequest request = webhookConfigFactory.buildConfig(subaccount.getWebhookToken());
        if (request == null) {
            return SubaccountWebhookRepairResponse.Item.builder()
                    .subaccountId(subaccount.getId())
                    .accountId(accountId)
                    .status("SKIPPED")
                    .message("ASAAS_WEBHOOK_URL is not configured")
                    .build();
        }

        try {
            String apiKey = asaasApiKeyResolver.resolveForSubaccount(subaccount.getId());
            AsaasWebhookConfigResponse response = asaasWebhookClient.createWebhook(apiKey, request);
            log.info("Repaired Asaas webhook for subaccountId={}, asaasWebhookId={}",
                    subaccount.getId(), response != null ? response.getId() : null);
            return SubaccountWebhookRepairResponse.Item.builder()
                    .subaccountId(subaccount.getId())
                    .accountId(accountId)
                    .status("SUCCEEDED")
                    .asaasWebhookId(response != null ? response.getId() : null)
                    .build();
        } catch (InvalidRequestException | ResourceNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to repair Asaas webhook for subaccountId={}: {}",
                    subaccount.getId(), ex.getMessage());
            return SubaccountWebhookRepairResponse.Item.builder()
                    .subaccountId(subaccount.getId())
                    .accountId(accountId)
                    .status("FAILED")
                    .message(ex.getMessage())
                    .build();
        }
    }
}
