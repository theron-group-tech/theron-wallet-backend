package com.theron.wallet.dto.response;

import com.theron.wallet.enums.OrganizationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationStatusResponse {

    private UUID id;
    private OrganizationStatus status;
}
