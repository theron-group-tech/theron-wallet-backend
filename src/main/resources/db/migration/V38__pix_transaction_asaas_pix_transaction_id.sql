ALTER TABLE pix_transaction
    ADD COLUMN IF NOT EXISTS asaas_pix_transaction_id VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_pix_transaction_asaas_pix_tx_id
    ON pix_transaction (asaas_pix_transaction_id)
    WHERE asaas_pix_transaction_id IS NOT NULL;
