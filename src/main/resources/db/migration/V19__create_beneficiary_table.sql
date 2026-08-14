-- Beneficiaries belong to an organization. Soft-delete via status INACTIVE.
CREATE TABLE beneficiary (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    created_by_user_id  UUID         NOT NULL REFERENCES app_user (id),
    name                VARCHAR(255) NOT NULL,
    document            VARCHAR(20),
    pix_key             VARCHAR(100),
    pix_key_type        VARCHAR(10),
    bank_code           VARCHAR(10),
    branch              VARCHAR(10),
    account             VARCHAR(20),
    account_type        VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_beneficiary_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_beneficiary_pix_key_type CHECK (
        pix_key_type IS NULL OR pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP')
    ),
    CONSTRAINT chk_beneficiary_account_type CHECK (
        account_type IS NULL OR account_type IN ('CHECKING', 'SAVINGS')
    ),
    CONSTRAINT chk_beneficiary_destination CHECK (
        (pix_key IS NOT NULL AND pix_key_type IS NOT NULL)
        OR (bank_code IS NOT NULL AND branch IS NOT NULL AND account IS NOT NULL AND account_type IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_beneficiary_org_pix_key
    ON beneficiary (organization_id, pix_key)
    WHERE pix_key IS NOT NULL;

CREATE UNIQUE INDEX uq_beneficiary_org_bank_account
    ON beneficiary (organization_id, bank_code, branch, account)
    WHERE bank_code IS NOT NULL;

CREATE INDEX idx_beneficiary_organization ON beneficiary (organization_id);

ALTER TABLE transaction ADD COLUMN beneficiary_id UUID REFERENCES beneficiary (id);
CREATE INDEX idx_transaction_beneficiary ON transaction (beneficiary_id);
