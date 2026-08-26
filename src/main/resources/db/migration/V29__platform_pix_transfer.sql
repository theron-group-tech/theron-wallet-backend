CREATE TABLE platform_pix_transfer (
    id                       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    asaas_transfer_id        VARCHAR(80)    NOT NULL UNIQUE,
    amount                   NUMERIC(19, 2) NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    destination_pix_key      VARCHAR(100)   NOT NULL,
    destination_pix_key_type VARCHAR(10)    NOT NULL,
    description              VARCHAR(255),
    idempotency_key          VARCHAR(120)   NOT NULL UNIQUE,
    created_at               TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform_pix_transfer_status CHECK (status IN (
        'PENDING', 'PENDING_APPROVAL', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'
    ))
);

CREATE INDEX idx_platform_pix_transfer_status
    ON platform_pix_transfer (status);
