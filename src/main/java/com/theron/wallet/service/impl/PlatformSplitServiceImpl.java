package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasSplitItem;
import com.theron.wallet.dto.request.UpdateSplitConfigRequest;
import com.theron.wallet.dto.response.SplitConfigResponse;
import com.theron.wallet.entity.PlatformSplitConfig;
import com.theron.wallet.enums.AuditAction;
import com.theron.wallet.exception.InvalidRequestException;
import com.theron.wallet.repository.PlatformSplitConfigRepository;
import com.theron.wallet.service.AuditLogService;
import com.theron.wallet.service.PlatformSplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSplitServiceImpl implements PlatformSplitService {

    private final PlatformSplitConfigRepository repository;
    private final AsaasProperties asaasProperties;
    private final AuditLogService auditLogService;

    @Value("${platform.split.percent:0}")
    private BigDecimal envPercent;

    @Value("${platform.split.fixed-amount:0}")
    private BigDecimal envFixedAmount;

    @Value("${platform.split.enabled:true}")
    private boolean envEnabled;

    @Override
    @Transactional(readOnly = true)
    public SplitConfigResponse getConfig() {
        return toResponse(load());
    }

    @Override
    @Transactional
    public SplitConfigResponse update(UpdateSplitConfigRequest request, UUID adminId) {
        PlatformSplitConfig config = load();
        if (request.getPercent() != null) {
            if (request.getPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidRequestException("percent must be between 0 and 100");
            }
            config.setPercent(request.getPercent());
        }
        if (request.getFixedAmount() != null) {
            config.setFixedAmount(request.getFixedAmount());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        config = repository.save(config);
        auditLogService.recordAdmin(
                AuditAction.ADMIN_SPLIT_UPDATED,
                null,
                adminId,
                "PlatformSplitConfig",
                null,
                Map.of(
                        "percent", config.getPercent(),
                        "fixedAmount", config.getFixedAmount(),
                        "enabled", config.getEnabled()));
        return toResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public void applyToPayment(AsaasPaymentRequest paymentRequest) {
        PlatformSplitConfig config = load();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        String masterWalletId = asaasProperties.getMasterWalletId();
        if (masterWalletId == null || masterWalletId.isBlank()) {
            log.warn("Platform split enabled but ASAAS_MASTER_WALLET_ID is not set; skipping split");
            return;
        }
        boolean hasPercent = config.getPercent() != null && config.getPercent().compareTo(BigDecimal.ZERO) > 0;
        boolean hasFixed = config.getFixedAmount() != null && config.getFixedAmount().compareTo(BigDecimal.ZERO) > 0;
        if (!hasPercent && !hasFixed) {
            return;
        }
        AsaasSplitItem item = AsaasSplitItem.builder()
                .walletId(masterWalletId)
                .percentualValue(hasPercent ? config.getPercent() : null)
                .fixedValue(hasFixed ? config.getFixedAmount() : null)
                .build();
        paymentRequest.setSplit(List.of(item));
    }

    private PlatformSplitConfig load() {
        return repository.findById((short) 1).orElseGet(() -> repository.save(PlatformSplitConfig.builder()
                .id((short) 1)
                .percent(envPercent == null ? BigDecimal.ZERO : envPercent)
                .fixedAmount(envFixedAmount == null ? BigDecimal.ZERO : envFixedAmount)
                .enabled(envEnabled)
                .build()));
    }

    private SplitConfigResponse toResponse(PlatformSplitConfig config) {
        String master = asaasProperties.getMasterWalletId();
        return SplitConfigResponse.builder()
                .percent(config.getPercent())
                .fixedAmount(config.getFixedAmount())
                .enabled(config.getEnabled())
                .masterWalletId(master == null || master.isBlank() ? null : master)
                .build();
    }
}
