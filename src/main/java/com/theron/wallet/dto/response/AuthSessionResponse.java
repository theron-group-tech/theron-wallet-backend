package com.theron.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionResponse {

    private UUID id;
    private UUID deviceId;
    private String deviceName;
    private String platform;
    private String ip;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
}
