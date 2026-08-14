package com.theron.wallet.dto.request;

import com.theron.wallet.enums.BankAccountType;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.PixKeyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Partial update. organizationId is immutable.")
public class UpdateBeneficiaryRequest {

    @Size(max = 255)
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
    private String account;

    @Schema(allowableValues = {"CHECKING", "SAVINGS"})
    private BankAccountType accountType;

    @Schema(allowableValues = {"ACTIVE", "INACTIVE"})
    private BeneficiaryStatus status;
}
