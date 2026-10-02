package com.theron.wallet.service.partnerwebhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class PartnerWebhookSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private PartnerWebhookSigner() {
    }

    public static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute partner webhook HMAC", ex);
        }
    }
}
