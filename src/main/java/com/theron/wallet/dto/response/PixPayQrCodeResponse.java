package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PixPayQrCodeResponse {

    /** Asaas PIX transaction id (poll via GET /pix/transactions/{id}). */
    private String id;
    private UUID accountId;
    private UUID transactionId;
    private UUID pixTransactionId;
    private BigDecimal amount;
    private TransactionStatus status;
    private String providerStatus;
    private String transferId;
    private String refusalReason;
    private String recipientName;
    private String recipientDocument;
    private String institutionName;
    private String description;
    private String endToEndIdentifier;
}
