package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.config.VemComigoSplitProperties;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSplitServiceImpl implements PlatformSplitService {

    private static final int MONEY_SCALE = 2;

    private final PlatformSplitConfigRepository repository;
    private final AsaasProperties asaasProperties;
    private final AuditLogService auditLogService;
    private final VemComigoSplitProperties vemComigoSplitProperties;

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
        applyToPayment(paymentRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public void applyToPayment(AsaasPaymentRequest paymentRequest, UUID organizationId) {
        if (isVemComigo(organizationId) && hasCounterpartySplits(paymentRequest)) {
            applyVemComigoResidualSplit(paymentRequest);
            return;
        }
        applyLegacyPlatformSplit(paymentRequest);
    }

    private boolean isVemComigo(UUID organizationId) {
        UUID configured = vemComigoSplitProperties.getOrganizationId();
        return organizationId != null && configured != null && configured.equals(organizationId);
    }

    private static boolean hasCounterpartySplits(AsaasPaymentRequest paymentRequest) {
        return paymentRequest.getSplit() != null && !paymentRequest.getSplit().isEmpty();
    }

    private void applyVemComigoResidualSplit(AsaasPaymentRequest paymentRequest) {
        String masterWalletId = asaasProperties.getMasterWalletId();
        if (masterWalletId == null || masterWalletId.isBlank()) {
            log.warn("Vem Comigo residual split skipped: ASAAS_MASTER_WALLET_ID is not set");
            return;
        }
        BigDecimal value = paymentRequest.getValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Vem Comigo residual split skipped: payment value missing or non-positive");
            return;
        }

        BigDecimal counterpartyTotal = sumCounterpartyAmounts(paymentRequest.getSplit(), value);
        BigDecimal residual = value.subtract(counterpartyTotal).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (residual.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Vem Comigo residual split skipped: residual={} (value={}, counterpartyTotal={})",
                    residual, value, counterpartyTotal);
            return;
        }

        BigDecimal commissionPercent = vemComigoSplitProperties.getCommissionPercent() == null
                ? BigDecimal.ZERO
                : vemComigoSplitProperties.getCommissionPercent();
        BigDecimal theron = residual
                .multiply(commissionPercent)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        if (theron.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        appendSplit(paymentRequest, AsaasSplitItem.builder()
                .walletId(masterWalletId)
                .fixedValue(theron)
                .build());
        log.debug("Vem Comigo residual platform split: residual={}, theronFixed={}", residual, theron);
    }

    static BigDecimal sumCounterpartyAmounts(List<AsaasSplitItem> splits, BigDecimal chargeValue) {
        BigDecimal sum = BigDecimal.ZERO;
        if (splits == null) {
            return sum;
        }
        for (AsaasSplitItem item : splits) {
            sum = sum.add(counterpartyAmount(item, chargeValue));
        }
        return sum.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal counterpartyAmount(AsaasSplitItem item, BigDecimal chargeValue) {
        if (item.getFixedValue() != null && item.getFixedValue().compareTo(BigDecimal.ZERO) > 0) {
            return item.getFixedValue().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (item.getPercentualValue() != null && item.getPercentualValue().compareTo(BigDecimal.ZERO) > 0) {
            return chargeValue
                    .multiply(item.getPercentualValue())
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void applyLegacyPlatformSplit(AsaasPaymentRequest paymentRequest) {
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
        appendSplit(paymentRequest, AsaasSplitItem.builder()
                .walletId(masterWalletId)
                .percentualValue(hasPercent ? config.getPercent() : null)
                .fixedValue(hasFixed ? config.getFixedAmount() : null)
                .build());
    }

    private static void appendSplit(AsaasPaymentRequest paymentRequest, AsaasSplitItem item) {
        ArrayList<AsaasSplitItem> splits = new ArrayList<>();
        if (paymentRequest.getSplit() != null) {
            splits.addAll(paymentRequest.getSplit());
        }
        splits.add(item);
        paymentRequest.setSplit(splits);
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
