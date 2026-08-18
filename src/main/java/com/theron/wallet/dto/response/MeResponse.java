package com.theron.wallet.dto.response;

import com.theron.wallet.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private List<UserOrganizationResponse> organizations;
}
