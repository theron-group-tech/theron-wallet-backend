package com.theron.wallet.dto.request;

import com.theron.wallet.enums.AccountStatus;
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
@Schema(description = "Partial update. type, currency and organizationId are immutable.")
public class UpdateAccountRequest {

    @Size(max = 255)
    private String name;

    @Schema(allowableValues = {"ACTIVE", "SUSPENDED", "CLOSED"})
    private AccountStatus status;
}
