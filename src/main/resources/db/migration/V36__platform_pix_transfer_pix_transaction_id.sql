ALTER TABLE platform_pix_transfer
    ADD COLUMN asaas_pix_transaction_id VARCHAR(80) NULL;

CREATE UNIQUE INDEX uq_platform_pix_transfer_pix_tx_id
    ON platform_pix_transfer (asaas_pix_transaction_id)
    WHERE asaas_pix_transaction_id IS NOT NULL;
