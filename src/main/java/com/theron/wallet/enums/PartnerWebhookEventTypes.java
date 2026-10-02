package com.theron.wallet.enums;

/**
 * Outbound partner webhook event types (Theron → Vem Comigo).
 */
public final class PartnerWebhookEventTypes {

    public static final String CHARGE_RECEIVED = "charge.received";
    public static final String CHARGE_CANCELLED = "charge.cancelled";
    public static final String CHARGE_REFUNDED = "charge.refunded";
    public static final String PIX_INBOUND_RECEIVED = "pix.inbound.received";
    public static final String PIX_TRANSFER_COMPLETED = "pix.transfer.completed";
    public static final String PIX_TRANSFER_FAILED = "pix.transfer.failed";
    public static final String TRANSFER_INBOUND_COMPLETED = "transfer.inbound.completed";
    public static final String TRANSFER_OUTBOUND_COMPLETED = "transfer.outbound.completed";
    public static final String TRANSFER_OUTBOUND_FAILED = "transfer.outbound.failed";

    private PartnerWebhookEventTypes() {
    }
}
