CREATE TABLE audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         REFERENCES organization (id),
    user_id         UUID         REFERENCES app_user (id),
    action          VARCHAR(40)  NOT NULL,
    resource_type   VARCHAR(80),
    resource_id     VARCHAR(64),
    ip              VARCHAR(45),
    user_agent      VARCHAR(512),
    device_id       VARCHAR(128),
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_audit_log_action CHECK (action IN (
        'LOGIN',
        'LOGOUT',
        'PASSWORD_CHANGED',
        'USER_CREATED',
        'USER_DISABLED',
        'MEMBER_ADDED',
        'MEMBER_REMOVED',
        'ROLE_CHANGED',
        'PIX_KEY_CREATED',
        'PIX_KEY_REMOVED',
        'TRANSFER_CREATED',
        'TRANSFER_APPROVED',
        'TRANSFER_REJECTED',
        'BENEFICIARY_CREATED',
        'BENEFICIARY_UPDATED',
        'BENEFICIARY_DELETED',
        'LIMIT_CHANGED'
    ))
);

CREATE INDEX idx_audit_log_org_created
    ON audit_log (organization_id, created_at DESC);

CREATE INDEX idx_audit_log_user_created
    ON audit_log (user_id, created_at DESC);

CREATE INDEX idx_audit_log_action
    ON audit_log (action);

CREATE OR REPLACE FUNCTION prevent_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_mutation();
