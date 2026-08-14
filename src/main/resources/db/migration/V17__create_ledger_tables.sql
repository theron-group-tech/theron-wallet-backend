-- Double-entry ledger (source of truth in parallel with wallet.balance)
CREATE TABLE ledger_account (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID         UNIQUE REFERENCES account (id),
    organization_id UUID         NOT NULL REFERENCES organization (id),
    kind            VARCHAR(20)  NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ledger_account_kind CHECK (kind IN ('CUSTOMER', 'CLEARING')),
    CONSTRAINT chk_ledger_account_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_ledger_account_owner CHECK (
        (kind = 'CUSTOMER' AND account_id IS NOT NULL)
        OR (kind = 'CLEARING' AND account_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_ledger_clearing_org_currency
    ON ledger_account (organization_id, currency)
    WHERE kind = 'CLEARING';

CREATE INDEX idx_ledger_account_organization ON ledger_account (organization_id);

CREATE TABLE ledger_transaction (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reference        VARCHAR(100),
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    idempotency_key  VARCHAR(120) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ledger_transaction_type CHECK (type IN ('CREDIT', 'DEBIT', 'TRANSFER')),
    CONSTRAINT chk_ledger_transaction_status CHECK (status IN ('POSTED')),
    CONSTRAINT uq_ledger_transaction_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entry (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id     UUID           NOT NULL REFERENCES ledger_transaction (id),
    ledger_account_id  UUID           NOT NULL REFERENCES ledger_account (id),
    direction          VARCHAR(10)    NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    created_at         TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entry_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entry_transaction ON ledger_entry (transaction_id);
CREATE INDEX idx_ledger_entry_ledger_account ON ledger_entry (ledger_account_id);
