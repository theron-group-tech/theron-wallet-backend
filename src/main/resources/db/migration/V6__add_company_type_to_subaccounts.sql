ALTER TABLE subaccount
    ADD COLUMN IF NOT EXISTS company_type VARCHAR(50);

COMMENT ON COLUMN subaccount.company_type
    IS 'Required by Asaas for CNPJ accounts: MEI, LIMITED, INDIVIDUAL, ASSOCIATION. Null for CPF customers.';
