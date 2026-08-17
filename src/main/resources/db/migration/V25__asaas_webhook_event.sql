CREATE TABLE asaas_webhook_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    asaas_event_id  VARCHAR(120) NOT NULL UNIQUE,
    event           VARCHAR(80)  NOT NULL,
    resource_id     VARCHAR(80),
    status          VARCHAR(20)  NOT NULL,
    payload         JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,
    CONSTRAINT chk_asaas_webhook_event_status CHECK (status IN (
        'RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'
    ))
);

CREATE INDEX idx_asaas_webhook_event_resource
    ON asaas_webhook_event (resource_id);

CREATE TABLE asaas_reconciliation (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          REFERENCES transaction (id),
    asaas_id        VARCHAR(80)   NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    local_status    VARCHAR(40),
    asaas_status    VARCHAR(40),
    local_amount    NUMERIC(19, 2),
    asaas_amount    NUMERIC(19, 2),
    detail          VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_asaas_reconciliation_kind CHECK (kind IN ('PAYMENT', 'TRANSFER')),
    CONSTRAINT chk_asaas_reconciliation_status CHECK (status IN (
        'MATCHED', 'PENDING', 'DIVERGENT', 'FAILED'
    ))
);

CREATE INDEX idx_asaas_reconciliation_tx
    ON asaas_reconciliation (transaction_id);

CREATE INDEX idx_asaas_reconciliation_asaas
    ON asaas_reconciliation (asaas_id);
