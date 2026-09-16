package com.theron.wallet.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PixEmvPayloadUtilsTest {

    private static final String SAMPLE_PAYLOAD =
            "00020126580014br.gov.bcb.pix01365d276f1d-6264-45e4-bc5b-d01a4f90d7c5520400005303986540510.005802BR5912iFriend Bank6013Medeiros Neto62290525IFRIENDB00000001770524ASA63040092";

    @Test
    void parseTransactionAmountReadsTag54() {
        assertThat(PixEmvPayloadUtils.parseTransactionAmount(SAMPLE_PAYLOAD))
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void parseTransactionAmountReturnsNullWhenTag54Absent() {
        assertThat(PixEmvPayloadUtils.parseTransactionAmount("000201010212"))
                .isNull();
    }

    @Test
    void resolveQrCodeValuePrefersAsaasValue() {
        assertThat(PixEmvPayloadUtils.resolveQrCodeValue(
                        new BigDecimal("25.00"), new BigDecimal("10.00"), SAMPLE_PAYLOAD))
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void resolveQrCodeValueFallsBackToPayload() {
        assertThat(PixEmvPayloadUtils.resolveQrCodeValue(null, null, SAMPLE_PAYLOAD))
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }
}
