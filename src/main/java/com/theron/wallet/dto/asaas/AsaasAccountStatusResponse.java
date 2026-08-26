package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasAccountStatusResponse {

    private String id;
    private String commercialInfo;
    private String bankAccountInfo;
    private String documentation;
    private String general;
}
