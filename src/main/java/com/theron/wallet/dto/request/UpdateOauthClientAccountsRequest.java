package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Replace the single Account binding on an OAuth client (1:1)")
public class UpdateOauthClientAccountsRequest {

    @NotEmpty
    @Size(min = 1, max = 1)
    @Schema(description = "Exactly one Account ID — credentials remain bound 1:1 to that account",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    private List<UUID> accountIds;
}
