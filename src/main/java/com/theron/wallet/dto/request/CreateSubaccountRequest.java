package com.theron.wallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class CreateSubaccountRequest {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotNull(message = "Income value is required")
    @DecimalMin(value = "0.00", message = "Income value must be non-negative")
    private BigDecimal incomeValue;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @NotBlank(message = "Address number is required")
    @Size(max = 20, message = "Address number must be at most 20 characters")
    private String addressNumber;

    @Size(max = 100, message = "Complement must be at most 100 characters")
    private String complement;

    @NotBlank(message = "Province is required")
    @Size(max = 100, message = "Province must be at most 100 characters")
    private String province;

    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "\\d{8}", message = "Postal code must have exactly 8 digits")
    private String postalCode;
}
