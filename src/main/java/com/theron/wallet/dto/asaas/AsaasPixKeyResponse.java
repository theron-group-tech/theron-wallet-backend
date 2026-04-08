package com.theron.wallet.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasPixKeyResponse {
    private String id;
    private String key;
    private String type;
    private String status;
    private String city;
    private String state;
    private String pixKeyType;
    private Boolean canBeDeleted;
    private Boolean canBePortabilityRequested;
    private String dateCreated;
}
