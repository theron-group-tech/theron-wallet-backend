-- V4__create_subaccount_table.sql
-- Theron Wallet Service — Subaccount entity for Asaas multi-tenant support

CREATE TABLE subaccount (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID          NOT NULL UNIQUE REFERENCES customer(id),
    asaas_account_id     VARCHAR(50)   UNIQUE,
    asaas_wallet_id      VARCHAR(50)   UNIQUE,
    status               VARCHAR(30)   NOT NULL DEFAULT 'PROVISIONING',
    encrypted_api_key    BYTEA,
    webhook_token        VARCHAR(128),
    income_value         DECIMAL(19, 2) NOT NULL,
    address              VARCHAR(255)  NOT NULL,
    address_number       VARCHAR(20)   NOT NULL,
    complement           VARCHAR(100),
    province             VARCHAR(100)  NOT NULL,
    postal_code          VARCHAR(10)   NOT NULL,
    status_reason        VARCHAR(500),
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subaccount_customer_id ON subaccount(customer_id);
CREATE INDEX idx_subaccount_asaas_account_id ON subaccount(asaas_account_id);
CREATE INDEX idx_subaccount_status ON subaccount(status);
CREATE INDEX idx_subaccount_webhook_token ON subaccount(webhook_token);

-- Only valid statuses
ALTER TABLE subaccount ADD CONSTRAINT chk_subaccount_status
    CHECK (status IN ('PROVISIONING', 'PENDING_EVALUATION', 'ACTIVE', 'FAILED', 'EVALUATION_BLOCKED', 'SUSPENDED'));
