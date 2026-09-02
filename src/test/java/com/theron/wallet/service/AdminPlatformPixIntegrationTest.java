package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.request.CreatePlatformPixPayQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminPlatformPixIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /admin/platform-account/pix/qr-codes returns payload with admin token")
    void createQrCodeReturnsPayload() throws Exception {
        when(asaasPixClient.listPixKeys("test-key")).thenReturn(
                AsaasListResponse.<AsaasPixKeyResponse>builder()
                        .data(List.of(AsaasPixKeyResponse.builder()
                                .id("pix-key-asaas-1")
                                .key("evp-admin-key-1")
                                .type("EVP")
                                .status("ACTIVE")
                                .build()))
                        .build());
        when(asaasPixClient.createStaticQrCode(eq("test-key"), eq("evp-admin-key-1"), any()))
                .thenReturn(AsaasPixStaticQrCodeResponse.builder()
                        .payload("00020126...")
                        .encodedImage("data:image/png;base64,abc")
                        .value(new BigDecimal("25.00"))
                        .description("Cobrança teste")
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixQrCodeRequest.builder()
                                .pixKeyId("pix-key-asaas-1")
                                .value(new BigDecimal("25.00"))
                                .description("Cobrança teste")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").value("00020126..."))
                .andExpect(jsonPath("$.encodedImage").value("data:image/png;base64,abc"))
                .andExpect(jsonPath("$.value").value(25.00))
                .andExpect(jsonPath("$.description").value("Cobrança teste"));
    }

    @Test
    @DisplayName("POST /admin/platform-account/pix/qr-codes/pay pays copia e cola with admin token")
    void payQrCodeReturnsStatus() throws Exception {
        when(asaasPixClient.payQrCode(eq("test-key"), any(), eq("idem-pay-1")))
                .thenReturn(AsaasPixPayQrCodeResponse.builder()
                        .id("pix-tx-1")
                        .status("DONE")
                        .value(new BigDecimal("10.00"))
                        .description("Test pay")
                        .externalAccount(AsaasPixPayQrCodeResponse.ExternalAccount.builder()
                                .name("Recebedor Teste")
                                .cpfCnpj("***.456.789-**")
                                .ispbName("Banco Teste")
                                .build())
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload("00020126580014br.gov.bcb.pix0136test")
                                .amount(new BigDecimal("10.00"))
                                .description("Test pay")
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("pix-tx-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.recipientName").value("Recebedor Teste"));
    }
}
