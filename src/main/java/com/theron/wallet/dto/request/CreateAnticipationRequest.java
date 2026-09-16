package com.theron.wallet.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnticipationRequest {

    /**
     * Local charge IDs belonging to the authenticated account (preferred).
     * Resolved to Asaas payment IDs server-side.
     */
    private List<UUID> chargeIds;

    /** Raw Asaas payment IDs — must belong to charges of the authenticated account. */
    private List<String> paymentIds;
}
