package com.theron.wallet.dto.request;

import com.theron.wallet.enums.BankAccountType;
import com.theron.wallet.enums.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a beneficiary under an organization. Provide a complete PIX destination or a complete bank destination.")
public class CreateBeneficiaryRequest {

    @NotNull
    private UUID organizationId;

    @NotBlank
    @Size(max = 255)
    @Schema(example = "Fornecedor ACME")
    private String name;

    @Size(max = 20)
    private String document;

    @Size(max = 100)
    private String pixKey;

    @Schema(allowableValues = {"CPF", "CNPJ", "EMAIL", "PHONE", "EVP"})
    private PixKeyType pixKeyType;

    @Size(max = 10)
    private String bankCode;

    @Size(max = 10)
    private String branch;

    @Size(max = 20)
    @Schema(description = "Bank account number")
    private String account;

    @Schema(allowableValues = {"CHECKING", "SAVINGS"})
    private BankAccountType accountType;
}
