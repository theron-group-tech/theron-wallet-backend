ALTER TABLE platform_pix_transfer
    ALTER COLUMN asaas_transfer_id DROP NOT NULL;

ALTER TABLE platform_pix_transfer
    DROP CONSTRAINT platform_pix_transfer_asaas_transfer_id_key;

CREATE UNIQUE INDEX uq_platform_pix_transfer_asaas_transfer_id
    ON platform_pix_transfer (asaas_transfer_id)
    WHERE asaas_transfer_id IS NOT NULL;
