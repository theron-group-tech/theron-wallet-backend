-- Account ↔ Asaas Subaccount (1:1). Subaccount is the Asaas rail for the Theron Account.
ALTER TABLE subaccount
    ADD COLUMN account_id UUID UNIQUE REFERENCES account (id);

CREATE UNIQUE INDEX uq_subaccount_account
    ON subaccount (account_id)
    WHERE account_id IS NOT NULL;

-- Minimal financial limits per Account (expanded in Module 11)
CREATE TABLE account_limit (
    account_id            UUID           PRIMARY KEY REFERENCES account (id),
    max_operation_amount  NUMERIC(19, 2) NOT NULL,
    daily_limit_amount    NUMERIC(19, 2) NOT NULL,
    updated_at            TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_account_limit_positive CHECK (
        max_operation_amount > 0 AND daily_limit_amount > 0
    )
);

-- Local PIX address keys owned by Account (synced with Asaas)
CREATE TABLE pix_key (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID         NOT NULL REFERENCES account (id),
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    type             VARCHAR(10)  NOT NULL,
    key              VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    provider_key_id  VARCHAR(50),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pix_key_type CHECK (type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP')),
    CONSTRAINT chk_pix_key_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'ERROR'))
);

CREATE UNIQUE INDEX uq_pix_key_account_key
    ON pix_key (account_id, key)
    WHERE key IS NOT NULL;

CREATE INDEX idx_pix_key_account ON pix_key (account_id);

-- PIX transfer projection linked to internal transaction (no REFUNDED — use REVERSED)
CREATE TABLE pix_transaction (
    id                        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id            UUID         NOT NULL UNIQUE REFERENCES transaction (id),
    account_id                UUID         NOT NULL REFERENCES account (id),
    pix_key_id                UUID         REFERENCES pix_key (id),
    destination_pix_key       VARCHAR(100),
    destination_pix_key_type  VARCHAR(10),
    beneficiary_id            UUID         REFERENCES beneficiary (id),
    provider_reference        VARCHAR(50),
    status                    VARCHAR(20)  NOT NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pix_transaction_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED')
    ),
    CONSTRAINT chk_pix_transaction_dest_type CHECK (
        destination_pix_key_type IS NULL
        OR destination_pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP')
    )
);

CREATE INDEX idx_pix_transaction_account ON pix_transaction (account_id);
CREATE INDEX idx_pix_transaction_provider ON pix_transaction (provider_reference);
