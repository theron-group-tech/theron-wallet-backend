package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Account pick-list for PaymentOrder destination — no balances. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentOrderDestinationResponse {

    private UUID accountId;
    private String accountName;
    private UUID ownerUserId;
    private String ownerName;
}
