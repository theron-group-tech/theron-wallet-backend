-- Platform Master Pix keys.
-- Unlike pix_key, these keys do not belong to an organization/account MAIN.
-- They identify destinations owned by the Theron platform itself.

CREATE TABLE platform_pix_key (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_account_id SMALLINT NOT NULL REFERENCES platform_account (id),
    type                VARCHAR(10) NOT NULL,
    key                 VARCHAR(100) NOT NULL UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    provider_key_id     VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform_pix_key_type CHECK (type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'EVP')),
    CONSTRAINT chk_platform_pix_key_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'ERROR'))
);

CREATE INDEX idx_platform_pix_key_status
    ON platform_pix_key (status);

-- Current Asaas Sandbox Master account Pix key used by the platform.
-- Keep this separate from pix_key because the platform Master is not an organization account.
INSERT INTO platform_pix_key (platform_account_id, type, key, status)
VALUES (1, 'EVP', '53b44609-1454-40b7-b56d-a26b166bfbec', 'ACTIVE')
ON CONFLICT (key) DO NOTHING;
