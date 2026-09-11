package com.theron.wallet.service.impl;

import com.theron.wallet.enums.ChargeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeStatusMappingTest {

    @Test
    void mapsAsaasStatuses() {
        assertThat(ChargeServiceImpl.mapAsaasStatus("PENDING")).isEqualTo(ChargeStatus.PENDING);
        assertThat(ChargeServiceImpl.mapAsaasStatus("RECEIVED")).isEqualTo(ChargeStatus.RECEIVED);
        assertThat(ChargeServiceImpl.mapAsaasStatus("CONFIRMED")).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(ChargeServiceImpl.mapAsaasStatus("OVERDUE")).isEqualTo(ChargeStatus.OVERDUE);
        assertThat(ChargeServiceImpl.mapAsaasStatus("REFUNDED")).isEqualTo(ChargeStatus.REFUNDED);
        assertThat(ChargeServiceImpl.mapAsaasStatus("DELETED")).isEqualTo(ChargeStatus.DELETED);
        assertThat(ChargeServiceImpl.mapAsaasStatus(null)).isEqualTo(ChargeStatus.PENDING);
    }
}
