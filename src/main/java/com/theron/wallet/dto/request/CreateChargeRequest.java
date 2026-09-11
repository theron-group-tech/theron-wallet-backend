package com.theron.wallet.dto.request;

import com.theron.wallet.enums.ChargeBillingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a multi-tenant Asaas charge. accountId comes from OAuth credentials only.")
public class CreateChargeRequest {

    @NotNull
    @Valid
    private ChargeCustomerRequest customer;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal value;

    @NotNull
    private ChargeBillingType billingType;

    @NotNull
    private LocalDate dueDate;

    @Size(max = 500)
    private String description;

    @Size(max = 100)
    private String externalReference;

    @Schema(description = "Installment count for CREDIT_CARD (optional)")
    private Integer installments;

    @Valid
    private List<ChargeSplitRequest> split;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeCustomerRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String cpfCnpj;
        private String email;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeSplitRequest {
        @NotBlank
        private String walletId;
        private BigDecimal percentualValue;
        private BigDecimal fixedValue;
    }
}
