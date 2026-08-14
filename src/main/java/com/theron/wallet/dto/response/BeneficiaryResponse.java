package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.BankAccountType;
import com.theron.wallet.enums.BeneficiaryStatus;
import com.theron.wallet.enums.PixKeyType;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BeneficiaryResponse {

    private UUID id;
    private UUID organizationId;
    private UUID createdByUserId;
    private String name;
    private String document;
    private String pixKey;
    private PixKeyType pixKeyType;
    private String bankCode;
    private String branch;
    private String account;
    private BankAccountType accountType;
    private BeneficiaryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
