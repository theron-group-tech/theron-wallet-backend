package com.theron.wallet.dto.request;

import com.theron.wallet.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a financial account under an organization")
public class CreateAccountRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(example = "Conta principal")
    private String name;

    @NotNull
    @Schema(allowableValues = {"MAIN", "EMPLOYEE", "RESERVE"})
    private AccountType type;

    @Size(min = 3, max = 3)
    @Schema(description = "ISO-4217 currency code. Defaults to BRL.", example = "BRL")
    private String currency;
}
