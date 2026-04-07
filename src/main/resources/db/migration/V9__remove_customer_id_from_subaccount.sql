-- V9: Remove customer_id FK from subaccount table.
-- Subaccounts are now fully standalone entities — no link to Customer.

ALTER TABLE subaccount DROP CONSTRAINT IF EXISTS fk_subaccount_customer;
ALTER TABLE subaccount DROP COLUMN IF EXISTS customer_id;

