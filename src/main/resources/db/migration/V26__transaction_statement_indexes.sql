CREATE INDEX idx_transaction_account_created
    ON transaction (account_id, created_at DESC);

CREATE INDEX idx_transaction_account_status
    ON transaction (account_id, status);
