package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

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
        when(asaasPixClient.createStaticQrCode(eq("test-key"), eq("pix-key-asaas-1"), any()))
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
}
