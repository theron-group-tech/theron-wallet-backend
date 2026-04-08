package com.theron.wallet.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PixKeyResponse {
    private String id;
    private String key;
    private String type;
    private String status;
    private Boolean canBeDeleted;
    private String dateCreated;
}
