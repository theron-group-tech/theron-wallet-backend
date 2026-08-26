ALTER TABLE platform_pix_transfer
    ADD COLUMN credit_transaction_id UUID NULL REFERENCES transaction (id);
