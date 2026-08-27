-- Source account is defined at approve time (OWNER who approves), not at create.
ALTER TABLE payment_order DROP CONSTRAINT IF EXISTS chk_payment_order_distinct_accounts;
ALTER TABLE payment_order ALTER COLUMN source_account_id DROP NOT NULL;
ALTER TABLE payment_order ADD CONSTRAINT chk_payment_order_distinct_accounts CHECK (
    source_account_id IS NULL OR source_account_id <> destination_account_id
);
