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
@Schema(description = "Replace membership roles. Server validates actor permissions; client-sent elevation is rejected.")
public class ReplaceMemberRolesRequest {

    @NotEmpty
    @Schema(description = "Role codes to assign", example = "[\"FINANCE\"]")
    private List<String> roleCodes;
}
