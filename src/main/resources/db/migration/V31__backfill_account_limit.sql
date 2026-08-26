-- Provision account_limit for existing accounts (PIX requires this row).
INSERT INTO account_limit (account_id, max_operation_amount, daily_limit_amount, updated_at)
SELECT a.id, 5000.00, 10000.00, NOW()
FROM account a
WHERE NOT EXISTS (
    SELECT 1 FROM account_limit al WHERE al.account_id = a.id
);
