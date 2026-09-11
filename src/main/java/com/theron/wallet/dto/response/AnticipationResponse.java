package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.AnticipationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnticipationResponse {

    private UUID id;
    private UUID organizationId;
    private UUID accountId;
    private String asaasAnticipationId;
    private AnticipationStatus status;
    private BigDecimal requestedValue;
    private BigDecimal netValue;
    private BigDecimal feeValue;
    private List<String> paymentIds;
    private String simulationJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
