package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubaccountWebhookRepairResponse {

    private int attempted;
    private int succeeded;
    private int skipped;
    private int failed;
    private String asaasWebhookId;
    private UUID subaccountId;
    private List<Item> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private UUID subaccountId;
        private UUID accountId;
        private String status;
        private String asaasWebhookId;
        private String message;
    }
}
