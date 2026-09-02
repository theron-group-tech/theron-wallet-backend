package com.theron.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theron.wallet.BaseIntegrationTest;
import com.theron.wallet.dto.asaas.AsaasPixPayQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasListResponse;
import com.theron.wallet.dto.asaas.AsaasPixKeyResponse;
import com.theron.wallet.dto.asaas.AsaasPixStaticQrCodeResponse;
import com.theron.wallet.dto.asaas.AsaasPixTransactionResponse;
import com.theron.wallet.dto.request.CreatePlatformPixPayQrCodeRequest;
import com.theron.wallet.dto.request.CreatePlatformPixQrCodeRequest;
import com.theron.wallet.entity.PlatformPixTransfer;
import com.theron.wallet.enums.TransactionStatus;
import com.theron.wallet.repository.PlatformPixTransferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminPlatformPixIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformPixTransferRepository platformPixTransferRepository;

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
                        .transferId("transfer-qr-1")
                        .status("DONE")
                        .value(new BigDecimal("10.00"))
                        .description("Test pay")
                        .externalAccount(AsaasPixPayQrCodeResponse.ExternalAccount.builder()
                                .name("Recebedor Teste")
                                .cpfCnpj("***.456.789-**")
                                .ispbName("Banco Teste")
                                .addressKey("evp-receiver-1")
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

        PlatformPixTransfer persisted = platformPixTransferRepository.findByAsaasTransferId("transfer-qr-1")
                .orElseThrow();
        assertThat(persisted.getIdempotencyKey()).isEqualTo("idem-pay-1");
        assertThat(persisted.getAsaasPixTransactionId()).isEqualTo("pix-tx-1");
        assertThat(persisted.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(persisted.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(persisted.getDestinationPixKey()).isEqualTo("evp-receiver-1");
    }

    @Test
    @DisplayName("transfer-validation approves QR pay via PIX_QR_CODE payload from Asaas")
    void transferValidationApprovesPixQrCodePayload() throws Exception {
        when(asaasPixClient.payQrCode(eq("test-key"), any(), eq("idem-pay-pix-qr-code")))
                .thenReturn(AsaasPixPayQrCodeResponse.builder()
                        .id("pix-tx-qr-code-validation")
                        .transferId("transfer-qr-code-validation")
                        .status("AWAITING_REQUEST")
                        .value(new BigDecimal("50.00"))
                        .description("QR pay PIX_QR_CODE validation")
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-pix-qr-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload("00020126580014br.gov.bcb.pix0136test")
                                .amount(new BigDecimal("50.00"))
                                .description("QR pay PIX_QR_CODE validation")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/webhooks/asaas/transfer-validation")
                        .header("asaas-access-token", "test-transfer-validation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PIX_QR_CODE",
                                  "pixQrCode": {
                                    "id": "pix-tx-qr-code-validation",
                                    "value": 50.00,
                                    "status": "AWAITING_REQUEST"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        PlatformPixTransfer persisted = platformPixTransferRepository
                .findByAsaasPixTransactionId("pix-tx-qr-code-validation")
                .orElseThrow();
        assertThat(persisted.getIdempotencyKey()).isEqualTo("idem-pay-pix-qr-code");
    }

    @Test
    @DisplayName("POST /webhooks/asaas/transfer-validation approves persisted QR pay transfer")
    void transferValidationApprovesPersistedQrPay() throws Exception {
        when(asaasPixClient.payQrCode(eq("test-key"), any(), eq("idem-pay-validation")))
                .thenReturn(AsaasPixPayQrCodeResponse.builder()
                        .id("pix-tx-validation")
                        .transferId("transfer-qr-validation")
                        .status("AWAITING_REQUEST")
                        .value(new BigDecimal("50.00"))
                        .description("QR pay validation")
                        .externalAccount(AsaasPixPayQrCodeResponse.ExternalAccount.builder()
                                .name("Recebedor Teste")
                                .addressKey("evp-receiver-validation")
                                .build())
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload("00020126580014br.gov.bcb.pix0136test")
                                .amount(new BigDecimal("50.00"))
                                .description("QR pay validation")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/webhooks/asaas/transfer-validation")
                        .header("asaas-access-token", "test-transfer-validation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TRANSFER",
                                  "transfer": {
                                    "id": "transfer-qr-validation",
                                    "value": 50.00,
                                    "status": "PENDING",
                                    "externalReference": "idem-pay-validation"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("transfer-validation approves QR pay when Asaas returns AWAITING_REQUEST without transferId")
    void transferValidationApprovesQrPayWithoutTransferIdInPayResponse() throws Exception {
        when(asaasPixClient.payQrCode(eq("test-key"), any(), eq("idem-pay-no-transfer-id")))
                .thenReturn(AsaasPixPayQrCodeResponse.builder()
                        .id("pix-tx-no-transfer-id")
                        .status("AWAITING_REQUEST")
                        .value(new BigDecimal("50.00"))
                        .description("QR pay without transfer id")
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-no-transfer-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload("00020126580014br.gov.bcb.pix0136test")
                                .amount(new BigDecimal("50.00"))
                                .description("QR pay without transfer id")
                                .build())))
                .andExpect(status().isCreated());

        PlatformPixTransfer reserved = platformPixTransferRepository
                .findByIdempotencyKey("idem-pay-no-transfer-id")
                .orElseThrow();
        assertThat(reserved.getAsaasTransferId()).isNull();
        assertThat(reserved.getAsaasPixTransactionId()).isEqualTo("pix-tx-no-transfer-id");
        assertThat(reserved.getStatus()).isEqualTo(TransactionStatus.PROCESSING);

        mockMvc.perform(post("/api/v1/webhooks/asaas/transfer-validation")
                        .header("asaas-access-token", "test-transfer-validation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TRANSFER",
                                  "transfer": {
                                    "id": "transfer-from-validation",
                                    "value": 50.00,
                                    "status": "PENDING",
                                    "externalReference": "idem-pay-no-transfer-id"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        PlatformPixTransfer bound = platformPixTransferRepository
                .findByAsaasTransferId("transfer-from-validation")
                .orElseThrow();
        assertThat(bound.getIdempotencyKey()).isEqualTo("idem-pay-no-transfer-id");
    }

    @Test
    @DisplayName("transfer-validation approves pending QR pay via amount fallback without externalReference")
    void transferValidationApprovesQrPayViaAmountFallback() throws Exception {
        when(asaasPixClient.payQrCode(eq("test-key"), any(), eq("idem-pay-amount-fallback")))
                .thenReturn(AsaasPixPayQrCodeResponse.builder()
                        .id("pix-tx-amount-fallback")
                        .status("AWAITING_REQUEST")
                        .value(new BigDecimal("75.00"))
                        .description("QR pay amount fallback")
                        .build());

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-amount-fallback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload("00020126580014br.gov.bcb.pix0136test")
                                .amount(new BigDecimal("75.00"))
                                .description("QR pay amount fallback")
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/webhooks/asaas/transfer-validation")
                        .header("asaas-access-token", "test-transfer-validation-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TRANSFER",
                                  "transfer": {
                                    "id": "transfer-amount-fallback",
                                    "value": 75.00,
                                    "status": "PENDING"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        PlatformPixTransfer bound = platformPixTransferRepository
                .findByAsaasTransferId("transfer-amount-fallback")
                .orElseThrow();
        assertThat(bound.getIdempotencyKey()).isEqualTo("idem-pay-amount-fallback");
    }

    @Test
    @DisplayName("POST /admin/platform-account/pix/qr-codes/pay rejects mismatched amount")
    void payQrCodeRejectsMismatchedAmount() throws Exception {
        String payload =
                "00020126580014br.gov.bcb.pix01365d276f1d-6264-45e4-bc5b-d01a4f90d7c5520400005303986540510.005802BR5912iFriend Bank6013Medeiros Neto62290525IFRIENDB00000001770524ASA63040092";

        mockMvc.perform(post("/api/v1/admin/platform-account/pix/qr-codes/pay")
                        .header("Authorization", "Bearer " + adminAccessToken())
                        .header("Idempotency-Key", "idem-pay-mismatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreatePlatformPixPayQrCodeRequest.builder()
                                .payload(payload)
                                .amount(new BigDecimal("5.00"))
                                .description("Mismatch")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Amount must match QR code value")));
    }

    @Test
    @DisplayName("GET /admin/platform-account/pix/transactions/{id} returns PIX transaction status")
    void getPixTransactionReturnsStatus() throws Exception {
        when(asaasPixClient.retrievePixTransaction(eq("test-key"), eq("pix-tx-awaiting")))
                .thenReturn(AsaasPixTransactionResponse.builder()
                        .id("pix-tx-awaiting")
                        .status("AWAITING_REQUEST")
                        .value(new BigDecimal("10.00"))
                        .transferId("transfer-1")
                        .externalAccount(AsaasPixTransactionResponse.ExternalAccount.builder()
                                .name("Recebedor Teste")
                                .build())
                        .build());

        mockMvc.perform(get("/api/v1/admin/platform-account/pix/transactions/pix-tx-awaiting")
                        .header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pix-tx-awaiting"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.providerStatus").value("AWAITING_REQUEST"))
                .andExpect(jsonPath("$.transferId").value("transfer-1"));
    }
}
