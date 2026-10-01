package com.theron.wallet.service.impl;

import com.theron.wallet.config.AsaasProperties;
import com.theron.wallet.config.VemComigoSplitProperties;
import com.theron.wallet.dto.asaas.AsaasPaymentRequest;
import com.theron.wallet.dto.asaas.AsaasSplitItem;
import com.theron.wallet.entity.PlatformSplitConfig;
import com.theron.wallet.repository.PlatformSplitConfigRepository;
import com.theron.wallet.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSplitServiceImplTest {

    private static final UUID VEM_COMIGO_ORG =
            UUID.fromString("565d0a47-6cd8-441a-b1de-766f042cd6d5");
    private static final String MASTER_WALLET = "wal_theron_master";

    @Mock private PlatformSplitConfigRepository repository;
    @Mock private AsaasProperties asaasProperties;
    @Mock private AuditLogService auditLogService;
    @Mock private VemComigoSplitProperties vemComigoSplitProperties;

    @InjectMocks private PlatformSplitServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "envPercent", BigDecimal.ZERO);
        ReflectionTestUtils.setField(service, "envFixedAmount", BigDecimal.ZERO);
        ReflectionTestUtils.setField(service, "envEnabled", true);
        org.mockito.Mockito.lenient().when(vemComigoSplitProperties.getOrganizationId()).thenReturn(VEM_COMIGO_ORG);
        org.mockito.Mockito.lenient().when(vemComigoSplitProperties.getCommissionPercent()).thenReturn(new BigDecimal("20"));
    }

    @Test
    @DisplayName("Vem Comigo: value 100 + counterparty 95% → Theron fixed 1.00")
    void vemComigoPercentCounterpartyYieldsOneRealTheronFee() {
        when(asaasProperties.getMasterWalletId()).thenReturn(MASTER_WALLET);

        AsaasPaymentRequest request = AsaasPaymentRequest.builder()
                .value(new BigDecimal("100.00"))
                .split(List.of(AsaasSplitItem.builder()
                        .walletId("wal_supplier")
                        .percentualValue(new BigDecimal("95"))
                        .build()))
                .build();

        service.applyToPayment(request, VEM_COMIGO_ORG);

        assertThat(request.getSplit()).hasSize(2);
        AsaasSplitItem platform = request.getSplit().get(1);
        assertThat(platform.getWalletId()).isEqualTo(MASTER_WALLET);
        assertThat(platform.getFixedValue()).isEqualByComparingTo("1.00");
        assertThat(platform.getPercentualValue()).isNull();
        assertThat(request.getSplit().get(0).getPercentualValue()).isEqualByComparingTo("95");
    }

    @Test
    @DisplayName("Vem Comigo: value 100 + counterparty fixed 10 → Theron fixed 18.00")
    void vemComigoFixedCounterpartyYieldsEighteenTheronFee() {
        when(asaasProperties.getMasterWalletId()).thenReturn(MASTER_WALLET);

        AsaasPaymentRequest request = AsaasPaymentRequest.builder()
                .value(new BigDecimal("100.00"))
                .split(List.of(AsaasSplitItem.builder()
                        .walletId("wal_supplier")
                        .fixedValue(new BigDecimal("10.00"))
                        .build()))
                .build();

        service.applyToPayment(request, VEM_COMIGO_ORG);

        AsaasSplitItem platform = request.getSplit().stream()
                .filter(s -> MASTER_WALLET.equals(s.getWalletId()))
                .findFirst()
                .orElseThrow();
        assertThat(platform.getFixedValue()).isEqualByComparingTo("18.00");
    }

    @Test
    @DisplayName("Other org: uses PlatformSplitConfig percent on total (legacy)")
    void otherOrgUsesLegacyPercentConfig() {
        when(asaasProperties.getMasterWalletId()).thenReturn(MASTER_WALLET);
        when(repository.findById((short) 1)).thenReturn(Optional.of(PlatformSplitConfig.builder()
                .id((short) 1)
                .percent(new BigDecimal("2"))
                .fixedAmount(BigDecimal.ZERO)
                .enabled(true)
                .build()));

        AsaasPaymentRequest request = AsaasPaymentRequest.builder()
                .value(new BigDecimal("100.00"))
                .split(List.of(AsaasSplitItem.builder()
                        .walletId("wal_supplier")
                        .percentualValue(new BigDecimal("95"))
                        .build()))
                .build();

        service.applyToPayment(request, UUID.randomUUID());

        AsaasSplitItem platform = request.getSplit().stream()
                .filter(s -> MASTER_WALLET.equals(s.getWalletId()))
                .findFirst()
                .orElseThrow();
        assertThat(platform.getPercentualValue()).isEqualByComparingTo("2");
        assertThat(platform.getFixedValue()).isNull();
    }

    @Test
    @DisplayName("Vem Comigo without counterparty splits falls back to legacy config")
    void vemComigoWithoutSplitsUsesLegacy() {
        when(asaasProperties.getMasterWalletId()).thenReturn(MASTER_WALLET);
        when(repository.findById((short) 1)).thenReturn(Optional.of(PlatformSplitConfig.builder()
                .id((short) 1)
                .percent(new BigDecimal("3"))
                .fixedAmount(BigDecimal.ZERO)
                .enabled(true)
                .build()));

        AsaasPaymentRequest request = AsaasPaymentRequest.builder()
                .value(new BigDecimal("100.00"))
                .build();

        service.applyToPayment(request, VEM_COMIGO_ORG);

        assertThat(request.getSplit()).hasSize(1);
        assertThat(request.getSplit().get(0).getPercentualValue()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Vem Comigo residual rounding: 3.33 × 20% → 0.67 HALF_UP")
    void vemComigoResidualRoundingHalfUp() {
        when(asaasProperties.getMasterWalletId()).thenReturn(MASTER_WALLET);

        // 100 - 96.67 = 3.33 residual → 3.33 * 0.20 = 0.666 → 0.67
        AsaasPaymentRequest request = AsaasPaymentRequest.builder()
                .value(new BigDecimal("100.00"))
                .split(List.of(AsaasSplitItem.builder()
                        .walletId("wal_supplier")
                        .fixedValue(new BigDecimal("96.67"))
                        .build()))
                .build();

        service.applyToPayment(request, VEM_COMIGO_ORG);

        AsaasSplitItem platform = request.getSplit().stream()
                .filter(s -> MASTER_WALLET.equals(s.getWalletId()))
                .findFirst()
                .orElseThrow();
        assertThat(platform.getFixedValue()).isEqualByComparingTo("0.67");
    }

    @Test
    @DisplayName("sumCounterpartyAmounts prefers fixedValue over percentual")
    void sumCounterpartyAmountsHelpers() {
        BigDecimal sum = PlatformSplitServiceImpl.sumCounterpartyAmounts(
                List.of(
                        AsaasSplitItem.builder().fixedValue(new BigDecimal("10.005")).build(),
                        AsaasSplitItem.builder().percentualValue(new BigDecimal("10")).build()),
                new BigDecimal("100.00"));
        // 10.01 (HALF_UP from 10.005) + 10.00 = 20.01
        assertThat(sum).isEqualByComparingTo("20.01");
    }
}
