ALTER TABLE transaction
    ADD COLUMN created_by_user_id UUID REFERENCES app_user (id);

CREATE INDEX idx_transaction_created_by ON transaction (created_by_user_id);

CREATE TABLE transaction_limit (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID           NOT NULL REFERENCES organization (id),
    account_id       UUID           REFERENCES account (id),
    user_id          UUID           REFERENCES app_user (id),
    role_id          UUID           REFERENCES role (id),
    transaction_type VARCHAR(20)    NOT NULL,
    period           VARCHAR(20)    NOT NULL,
    max_amount       NUMERIC(19, 2) NOT NULL,
    enabled          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transaction_limit_type CHECK (
        transaction_type IN ('PIX', 'TRANSFER', 'WITHDRAWAL', 'PAYMENT')
    ),
    CONSTRAINT chk_transaction_limit_period CHECK (
        period IN ('PER_TRANSACTION', 'DAILY', 'MONTHLY')
    ),
    CONSTRAINT chk_transaction_limit_amount CHECK (max_amount > 0),
    CONSTRAINT chk_transaction_limit_scope CHECK (
        (account_id IS NULL AND user_id IS NULL AND role_id IS NULL)
        OR (account_id IS NOT NULL AND user_id IS NULL AND role_id IS NULL)
        OR (account_id IS NULL AND user_id IS NOT NULL AND role_id IS NULL)
        OR (account_id IS NULL AND user_id IS NULL AND role_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_tx_limit_org
    ON transaction_limit (organization_id, transaction_type, period)
    WHERE account_id IS NULL AND user_id IS NULL AND role_id IS NULL;

CREATE UNIQUE INDEX uq_tx_limit_account
    ON transaction_limit (organization_id, account_id, transaction_type, period)
    WHERE account_id IS NOT NULL AND user_id IS NULL AND role_id IS NULL;

CREATE UNIQUE INDEX uq_tx_limit_user
    ON transaction_limit (organization_id, user_id, transaction_type, period)
    WHERE user_id IS NOT NULL AND account_id IS NULL AND role_id IS NULL;

CREATE UNIQUE INDEX uq_tx_limit_role
    ON transaction_limit (organization_id, role_id, transaction_type, period)
    WHERE role_id IS NOT NULL AND account_id IS NULL AND user_id IS NULL;

CREATE INDEX idx_tx_limit_org_type_enabled
    ON transaction_limit (organization_id, transaction_type, enabled);
