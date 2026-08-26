package com.theron.wallet.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theron.wallet.enums.PixKeyType;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PixKeyLookupResponse {

    private PixKeyType type;
    private String key;
    private String ispb;
    private String ispbName;
    private String institutionName;
    private String institutionCode;
    private String ownerName;
    private String ownerCpfCnpj;
}
