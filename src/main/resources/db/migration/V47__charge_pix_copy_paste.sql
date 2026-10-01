ALTER TABLE charge
    ADD COLUMN IF NOT EXISTS pix_copy_paste VARCHAR(2000);

ALTER TABLE charge
    ADD COLUMN IF NOT EXISTS pix_qr_code_expiration TIMESTAMP;
