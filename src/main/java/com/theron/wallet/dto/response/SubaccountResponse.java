package com.theron.wallet.dto.response;

import com.theron.wallet.enums.SubaccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubaccountResponse {

    private UUID id;
    private UUID customerId;
    private String asaasAccountId;
    private SubaccountStatus status;
    private String statusDescription;
    private BigDecimal incomeValue;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;
    private String statusReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
