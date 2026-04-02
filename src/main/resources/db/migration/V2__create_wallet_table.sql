-- V2__create_wallet_table.sql
-- Theron Wallet Service — Wallet entity (one per customer, holds balance)

CREATE TABLE wallet (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID           NOT NULL UNIQUE REFERENCES customer(id),
    balance       DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency      VARCHAR(3)     NOT NULL DEFAULT 'BRL',
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_customer_id ON wallet(customer_id);

-- Prevent negative balances at DB level
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0);
