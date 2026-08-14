package com.theron.wallet.dto.request;

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
@Schema(description = "Approve, reject or cancel an approval request")
public class ApprovalDecisionRequest {

    @Size(max = 500)
    private String comment;

    @Size(max = 120)
    private String idempotencyKey;
}
