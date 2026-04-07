ALTER TABLE subaccount
    ADD COLUMN IF NOT EXISTS birth_date VARCHAR(10);

COMMENT ON COLUMN subaccount.birth_date
    IS 'Date of birth (CPF) or company founding date (CNPJ) in yyyy-MM-dd format. Required by Asaas.';

