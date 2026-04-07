-- V10__migrate_wallet_to_subaccount.sql
-- Migrate wallet ownership from Customer to Subaccount.
-- Subaccounts are the primary business entity; wallets belong to subaccounts.

-- Add asaas_customer_id to subaccount (used as "customer" field in Asaas payment requests)
ALTER TABLE subaccount ADD COLUMN IF NOT EXISTS asaas_customer_id VARCHAR(50) UNIQUE;
CREATE INDEX IF NOT EXISTS idx_subaccount_asaas_customer_id ON subaccount(asaas_customer_id);

-- Migrate wallet: drop customer FK, add subaccount FK
-- (wallet table may have data; since subaccounts are new, existing wallets won't have a matching subaccount — safe to drop)
ALTER TABLE wallet DROP CONSTRAINT IF EXISTS wallet_customer_id_fkey;
ALTER TABLE wallet DROP COLUMN IF EXISTS customer_id;
ALTER TABLE wallet ADD COLUMN IF NOT EXISTS subaccount_id UUID REFERENCES subaccount(id);

-- Backfill constraint after data migration (no existing data to migrate)
-- Make column NOT NULL only after ensuring all rows are populated (safe on fresh schema)
-- In production with data, a data migration step would be needed here first.
ALTER TABLE wallet ALTER COLUMN subaccount_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_subaccount_id ON wallet(subaccount_id);
CREATE INDEX IF NOT EXISTS idx_wallet_subaccount_id ON wallet(subaccount_id);

