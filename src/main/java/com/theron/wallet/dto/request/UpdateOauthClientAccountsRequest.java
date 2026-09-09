package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Replace all account bindings on an OAuth client")
public class UpdateOauthClientAccountsRequest {

    @NotEmpty
    @Schema(description = "Account IDs this client may access")
    private List<UUID> accountIds;
}
