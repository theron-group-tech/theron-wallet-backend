-- V5__create_subaccount_api_key_audit_table.sql
-- Theron Wallet Service — Audit trail for subaccount API key lifecycle events

CREATE TABLE subaccount_api_key_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subaccount_id   UUID          NOT NULL REFERENCES subaccount(id),
    action          VARCHAR(30)   NOT NULL,
    performed_by    VARCHAR(100)  NOT NULL,
    ip_address      VARCHAR(45),
    details         VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_key_audit_subaccount_id ON subaccount_api_key_audit(subaccount_id);
CREATE INDEX idx_api_key_audit_action ON subaccount_api_key_audit(action);
CREATE INDEX idx_api_key_audit_created_at ON subaccount_api_key_audit(created_at);

-- Only valid audit actions
ALTER TABLE subaccount_api_key_audit ADD CONSTRAINT chk_api_key_audit_action
    CHECK (action IN ('CREATED', 'ROTATED', 'REVOKED', 'ACCESSED', 'DECRYPTION_FAILED'));
