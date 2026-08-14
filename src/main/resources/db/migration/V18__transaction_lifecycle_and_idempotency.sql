-- Transaction lifecycle (PENDING/PROCESSING/COMPLETED/FAILED/CANCELLED/REVERSED)
-- plus organization/account/currency/reference/completed_at/request_hash and idempotency fingerprint.

ALTER TABLE transaction ADD COLUMN organization_id UUID REFERENCES organization (id);
ALTER TABLE transaction ADD COLUMN account_id UUID REFERENCES account (id);
ALTER TABLE transaction ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE transaction ADD COLUMN reference VARCHAR(100);
ALTER TABLE transaction ADD COLUMN completed_at TIMESTAMP;
ALTER TABLE transaction ADD COLUMN request_hash VARCHAR(64);

ALTER TABLE transaction ALTER COLUMN idempotency_key TYPE VARCHAR(120);

UPDATE transaction SET status = 'COMPLETED' WHERE status = 'CONFIRMED';

ALTER TABLE transaction ADD CONSTRAINT chk_transaction_status CHECK (
    status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED')
);

ALTER TABLE transaction ADD CONSTRAINT chk_transaction_type CHECK (
    type IN (
        'DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT',
        'TRANSFER', 'PIX', 'PAYMENT', 'REFUND', 'FEE'
    )
);

CREATE INDEX idx_transaction_organization_id ON transaction (organization_id);
CREATE INDEX idx_transaction_account_id ON transaction (account_id);
