-- V3__create_transaction_table.sql
-- Theron Wallet Service — Transaction ledger for wallet operations

CREATE TABLE transaction (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id           UUID           NOT NULL REFERENCES wallet(id),
    type                VARCHAR(20)    NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    amount              DECIMAL(19, 2) NOT NULL,
    description         VARCHAR(255),
    asaas_payment_id    VARCHAR(50),
    external_reference  VARCHAR(100),
    idempotency_key     VARCHAR(100)   UNIQUE,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_wallet_id ON transaction(wallet_id);
CREATE INDEX idx_transaction_asaas_payment_id ON transaction(asaas_payment_id);
CREATE INDEX idx_transaction_status ON transaction(status);
CREATE INDEX idx_transaction_type ON transaction(type);
CREATE INDEX idx_transaction_idempotency_key ON transaction(idempotency_key);

-- Ensure amount is always positive (direction is encoded in type)
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0);
