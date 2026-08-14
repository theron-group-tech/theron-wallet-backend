-- Account: Theron financial account owned by Organization (independent of Asaas)
CREATE TABLE account (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    currency        VARCHAR(3)   NOT NULL DEFAULT 'BRL',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_account_type CHECK (type IN ('MAIN', 'EMPLOYEE', 'RESERVE')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_account_organization_status ON account (organization_id, status);

-- Compatibility: wallet may belong to an Account (new) and/or a Subaccount (legacy)
ALTER TABLE wallet ALTER COLUMN subaccount_id DROP NOT NULL;

ALTER TABLE wallet ADD COLUMN account_id UUID REFERENCES account (id);

CREATE UNIQUE INDEX uq_wallet_account_id ON wallet (account_id);

ALTER TABLE wallet ADD CONSTRAINT chk_wallet_has_owner
    CHECK (account_id IS NOT NULL OR subaccount_id IS NOT NULL);
