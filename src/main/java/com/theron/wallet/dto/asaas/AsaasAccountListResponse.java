package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasAccountListResponse {

    private List<AsaasSubaccountResponse> data;
    private boolean hasMore;
    private int totalCount;
}

