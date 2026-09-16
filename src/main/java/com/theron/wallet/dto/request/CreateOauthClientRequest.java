package com.theron.wallet.dto.request;

import com.theron.wallet.enums.OauthClientEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Create a B2B OAuth client bound 1:1 to a single Account within an organization")
public class CreateOauthClientRequest {

    @NotBlank
    @Schema(example = "ERP Integration")
    private String name;

    @Schema(allowableValues = {"SANDBOX", "PRODUCTION"}, description = "Defaults to SANDBOX")
    private OauthClientEnvironment environment;

    @NotEmpty
    @Schema(description = "Allowed B2B permission scopes", example = "[\"wallet.read\", \"pix.transfer\"]")
    private List<String> scopes;

    @NotEmpty
    @Size(min = 1, max = 1)
    @Schema(description = "Exactly one Account ID — credentials authenticate that account (1:1)",
            example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    private List<UUID> accountIds;
}
