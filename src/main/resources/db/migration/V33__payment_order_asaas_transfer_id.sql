ALTER TABLE payment_order
    ADD COLUMN asaas_transfer_id VARCHAR(50);

CREATE UNIQUE INDEX ux_payment_order_asaas_transfer_id
    ON payment_order (asaas_transfer_id)
    WHERE asaas_transfer_id IS NOT NULL;
