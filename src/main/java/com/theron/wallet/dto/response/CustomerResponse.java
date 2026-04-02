package com.theron.wallet.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private UUID id;
    private String name;
    private String email;
    private String cpfCnpj;
    private String phone;
    private String mobilePhone;
    private String asaasCustomerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
