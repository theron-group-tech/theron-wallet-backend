-- Account ownership (one financial account per person in an organization)
ALTER TABLE account
    ADD COLUMN owner_user_id UUID REFERENCES app_user (id);

CREATE UNIQUE INDEX uq_account_org_owner
    ON account (organization_id, owner_user_id)
    WHERE owner_user_id IS NOT NULL;

CREATE INDEX idx_account_owner_user_id ON account (owner_user_id);

-- Allow the same CPF/CNPJ on FAILED binds so another Account can retry independently
DROP INDEX IF EXISTS idx_subaccount_cpf_cnpj;
CREATE UNIQUE INDEX idx_subaccount_cpf_cnpj_non_failed
    ON subaccount (cpf_cnpj)
    WHERE cpf_cnpj IS NOT NULL AND status <> 'FAILED';

-- Platform split (single-row config)
CREATE TABLE platform_split_config (
    id            SMALLINT PRIMARY KEY DEFAULT 1,
    percent       NUMERIC(7, 4) NOT NULL DEFAULT 0,
    fixed_amount  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform_split_singleton CHECK (id = 1),
    CONSTRAINT chk_platform_split_percent CHECK (percent >= 0 AND percent <= 100),
    CONSTRAINT chk_platform_split_fixed CHECK (fixed_amount >= 0)
);

INSERT INTO platform_split_config (id, percent, fixed_amount, enabled)
VALUES (1, 0, 0, FALSE);

-- Audit: platform vs product actor
ALTER TABLE audit_log
    ADD COLUMN actor_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD COLUMN admin_id UUID REFERENCES admin_users (id);

ALTER TABLE audit_log
    ALTER COLUMN action TYPE VARCHAR(64);

ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS chk_audit_log_action;

ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'LOGIN',
    'LOGOUT',
    'PASSWORD_CHANGED',
    'USER_CREATED',
    'USER_DISABLED',
    'MEMBER_ADDED',
    'MEMBER_REMOVED',
    'ROLE_CHANGED',
    'PIX_KEY_CREATED',
    'PIX_KEY_REMOVED',
    'TRANSFER_CREATED',
    'TRANSFER_APPROVED',
    'TRANSFER_REJECTED',
    'BENEFICIARY_CREATED',
    'BENEFICIARY_UPDATED',
    'BENEFICIARY_DELETED',
    'LIMIT_CHANGED',
    'ADMIN_LOGIN',
    'ADMIN_SPLIT_UPDATED',
    'ADMIN_SUBACCOUNT_PROVISIONED',
    'ORGANIZATION_ADMIN_ASSIGNED',
    'ORGANIZATION_MEMBER_CREATED',
    'SUBACCOUNT_PROVISIONED'
));

ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_actor_type CHECK (actor_type IN ('USER', 'ADMIN'));

CREATE INDEX idx_audit_log_admin_created ON audit_log (admin_id, created_at DESC);
