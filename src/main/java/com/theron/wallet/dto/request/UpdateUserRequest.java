package com.theron.wallet.dto.request;

import com.theron.wallet.enums.UserStatus;
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
@Schema(description = "Partial update for a user. Email is immutable. Omit null fields.")
public class UpdateUserRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 20)
    private String phone;

    private UserStatus status;

    @Size(min = 8, max = 100)
    @Schema(description = "New plain password (optional)")
    private String password;
}
