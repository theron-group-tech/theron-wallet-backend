-- V8__refactor_subaccount_standalone.sql
-- Subaccounts are now independent from Customers.
-- customer_id becomes optional (nullable). Owner identity stored directly on the subaccount.

-- 1. Make customer_id nullable
ALTER TABLE subaccount ALTER COLUMN customer_id DROP NOT NULL;

-- 2. Add owner identity columns
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS name         VARCHAR(255);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS email        VARCHAR(255);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS login_email  VARCHAR(255);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS cpf_cnpj     VARCHAR(20);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS mobile_phone VARCHAR(20);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS phone        VARCHAR(20);
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS site         VARCHAR(500);

-- 3. cpf_cnpj is the primary uniqueness identifier for subaccounts
CREATE UNIQUE INDEX IF NOT EXISTS idx_subaccount_cpf_cnpj
    ON subaccount(cpf_cnpj)
    WHERE cpf_cnpj IS NOT NULL;

-- 4. Existing rows (if any) — backfill name/email/cpf_cnpj from linked customer
UPDATE subaccount s
SET name        = c.name,
    email       = c.email,
    cpf_cnpj    = c.cpf_cnpj,
    mobile_phone = c.mobile_phone,
    phone       = c.phone
FROM customer c
WHERE s.customer_id = c.id
  AND s.cpf_cnpj IS NULL;

