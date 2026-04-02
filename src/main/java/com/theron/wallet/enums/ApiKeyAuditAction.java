package com.theron.wallet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiKeyAuditAction {

    CREATED("API key initially stored after subaccount provisioning"),
    ROTATED("API key rotated — new key encrypted and old key replaced"),
    REVOKED("API key explicitly revoked"),
    ACCESSED("API key decrypted for use in an outbound call"),
    DECRYPTION_FAILED("API key decryption failed — possible key corruption or config mismatch");

    private final String description;
}
