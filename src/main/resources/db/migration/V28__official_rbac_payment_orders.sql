-- Official product RBAC: OWNER | FINANCE | EMPLOYEE
-- Payment orders, profile, granular members permissions
-- Platform account singleton + split defaults (enabled=true, 0%)

-- ---------------------------------------------------------------------------
-- Role.assignable
-- ---------------------------------------------------------------------------
ALTER TABLE role
    ADD COLUMN IF NOT EXISTS assignable BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE role SET assignable = FALSE WHERE code IN ('ADMIN', 'AUDITOR');
UPDATE role SET assignable = TRUE WHERE code IN ('OWNER', 'FINANCE', 'EMPLOYEE');

-- ---------------------------------------------------------------------------
-- New permissions
-- ---------------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    ('payment_orders.create', 'Create payment orders'),
    ('payment_orders.read', 'Read payment orders'),
    ('payment_orders.cancel', 'Cancel pending payment orders'),
    ('payment_orders.approve', 'Approve payment orders'),
    ('payment_orders.reject', 'Reject payment orders'),
    ('profile.read', 'Read own profile'),
    ('profile.update', 'Update own profile'),
    ('members.create', 'Create organization members'),
    ('members.update', 'Update organization members'),
    ('members.suspend', 'Suspend organization members'),
    ('members.activate', 'Activate organization members'),
    ('members.remove', 'Remove organization members'),
    ('members.role.update', 'Update member roles')
ON CONFLICT (code) DO NOTHING;

-- Alias: members.manage implies granular members.* for OWNER seed below

-- ---------------------------------------------------------------------------
-- Migrate memberships ADMIN / AUDITOR → EMPLOYEE
-- ---------------------------------------------------------------------------
UPDATE membership_role mr
SET role_id = (SELECT id FROM role WHERE code = 'EMPLOYEE')
WHERE mr.role_id IN (SELECT id FROM role WHERE code IN ('ADMIN', 'AUDITOR'));

-- Deduplicate if user already had EMPLOYEE
DELETE FROM membership_role a
USING membership_role b
WHERE a.id > b.id
  AND a.membership_id = b.membership_id
  AND a.role_id = b.role_id;

-- ---------------------------------------------------------------------------
-- Rebuild role_permission for product roles
-- ---------------------------------------------------------------------------
DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE code IN ('OWNER', 'FINANCE', 'EMPLOYEE', 'ADMIN', 'AUDITOR'));

-- OWNER: org + members + own wallet/pix + payment_orders all + profile + limits + audit
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
CROSS JOIN permission p
WHERE r.code = 'OWNER'
  AND p.code IN (
    'organization.read', 'organization.update',
    'users.read', 'users.create', 'users.update', 'users.disable',
    'members.read', 'members.manage',
    'members.create', 'members.update', 'members.suspend', 'members.activate', 'members.remove', 'members.role.update',
    'wallet.read', 'wallet.transfer',
    'transactions.read', 'transactions.create',
    'pix.read', 'pix.create', 'pix.transfer',
    'beneficiaries.read', 'beneficiaries.create', 'beneficiaries.update', 'beneficiaries.delete',
    'audit.read',
    'limits.read', 'limits.manage',
    'payment_orders.create', 'payment_orders.read', 'payment_orders.cancel',
    'payment_orders.approve', 'payment_orders.reject',
    'profile.read', 'profile.update',
    'approval.read', 'approval.create', 'approval.approve', 'approval.reject'
);

-- FINANCE: own wallet/pix + payment_orders create/read/cancel (no approve) + profile
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'wallet.read', 'wallet.transfer',
    'transactions.read', 'transactions.create',
    'pix.read', 'pix.create', 'pix.transfer',
    'beneficiaries.read', 'beneficiaries.create', 'beneficiaries.update', 'beneficiaries.delete',
    'limits.read',
    'payment_orders.create', 'payment_orders.read', 'payment_orders.cancel',
    'profile.read', 'profile.update'
)
WHERE r.code = 'FINANCE';

-- EMPLOYEE: own wallet/pix + profile (no payment_orders, no members)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'wallet.read', 'wallet.transfer',
    'transactions.read', 'transactions.create',
    'pix.read', 'pix.create', 'pix.transfer',
    'beneficiaries.read', 'beneficiaries.create', 'beneficiaries.update', 'beneficiaries.delete',
    'profile.read', 'profile.update'
)
WHERE r.code = 'EMPLOYEE';

-- ADMIN / AUDITOR: empty product permissions (non-assignable legacy rows)
-- (intentionally no inserts)

-- ---------------------------------------------------------------------------
-- Payment order
-- ---------------------------------------------------------------------------
CREATE TABLE payment_order (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID         NOT NULL REFERENCES organization (id),
    source_account_id       UUID         NOT NULL REFERENCES account (id),
    destination_account_id  UUID         NOT NULL REFERENCES account (id),
    amount                  NUMERIC(19, 2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'BRL',
    description             VARCHAR(500),
    status                  VARCHAR(30)  NOT NULL,
    created_by_user_id      UUID         NOT NULL REFERENCES app_user (id),
    decided_by_user_id      UUID         REFERENCES app_user (id),
    decision_comment        VARCHAR(500),
    debit_transaction_id    UUID         REFERENCES transaction (id),
    credit_transaction_id   UUID         REFERENCES transaction (id),
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    decided_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    CONSTRAINT chk_payment_order_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_order_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT chk_payment_order_status CHECK (status IN (
        'PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REJECTED', 'CANCELLED'
    ))
);

CREATE INDEX idx_payment_order_org_created ON payment_order (organization_id, created_at DESC);
CREATE INDEX idx_payment_order_status ON payment_order (organization_id, status);

-- ---------------------------------------------------------------------------
-- Platform account (singleton representation of Asaas Master)
-- ---------------------------------------------------------------------------
CREATE TABLE platform_account (
    id                      SMALLINT PRIMARY KEY DEFAULT 1,
    asaas_master_wallet_id  VARCHAR(100),
    label                   VARCHAR(100) NOT NULL DEFAULT 'Theron Platform',
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_platform_account_singleton CHECK (id = 1)
);

INSERT INTO platform_account (id, asaas_master_wallet_id, label)
VALUES (1, NULL, 'Theron Platform');

-- ---------------------------------------------------------------------------
-- Split defaults: enabled=true, percent=0, fixed=0
-- ---------------------------------------------------------------------------
UPDATE platform_split_config
SET percent = 0,
    fixed_amount = 0,
    enabled = TRUE,
    updated_at = NOW()
WHERE id = 1;

ALTER TABLE platform_split_config
    ALTER COLUMN enabled SET DEFAULT TRUE;

-- ---------------------------------------------------------------------------
-- Audit actions for payment orders / split
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log DROP CONSTRAINT IF EXISTS chk_audit_log_action;

ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
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
    'LIMIT_CHANGED',
    'ADMIN_LOGIN',
    'ADMIN_SPLIT_UPDATED',
    'ADMIN_SUBACCOUNT_PROVISIONED',
    'ORGANIZATION_ADMIN_ASSIGNED',
    'ORGANIZATION_MEMBER_CREATED',
    'SUBACCOUNT_PROVISIONED',
    'PAYMENT_ORDER_CREATED',
    'PAYMENT_ORDER_CANCELLED',
    'PAYMENT_ORDER_APPROVED',
    'PAYMENT_ORDER_REJECTED',
    'PAYMENT_ORDER_COMPLETED',
    'PAYMENT_ORDER_FAILED',
    'SPLIT_UPDATED'
));
