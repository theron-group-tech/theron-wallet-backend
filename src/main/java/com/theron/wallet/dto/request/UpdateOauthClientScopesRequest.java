package com.theron.wallet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Replace all scopes on an OAuth client")
public class UpdateOauthClientScopesRequest {

    @NotEmpty
    @Schema(description = "Allowed B2B permission scopes", example = "[\"wallet.read\", \"pix.transfer\"]")
    private List<String> scopes;
}
