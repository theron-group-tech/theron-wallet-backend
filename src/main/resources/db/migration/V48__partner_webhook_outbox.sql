CREATE TABLE partner_webhook_outbox (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    payload_json      TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    attempts          INT          NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_error        TEXT,
    delivered_at      TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_partner_webhook_outbox_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_partner_webhook_outbox_status CHECK (status IN (
        'PENDING', 'DELIVERING', 'DELIVERED', 'FAILED', 'DEAD'
    ))
);

CREATE INDEX idx_partner_webhook_outbox_poll
    ON partner_webhook_outbox (status, next_attempt_at);

CREATE INDEX idx_partner_webhook_outbox_org
    ON partner_webhook_outbox (organization_id);
