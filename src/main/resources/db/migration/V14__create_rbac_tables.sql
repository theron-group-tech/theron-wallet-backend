-- RBAC catalog (global) + membership roles
CREATE TABLE permission (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uq_permission_code UNIQUE (code)
);

CREATE TABLE role (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role (id),
    permission_id UUID NOT NULL REFERENCES permission (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE membership_role (
    id            UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID      NOT NULL REFERENCES organization_membership (id) ON DELETE CASCADE,
    role_id       UUID      NOT NULL REFERENCES role (id),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_membership_role UNIQUE (membership_id, role_id)
);

CREATE INDEX idx_membership_role_membership ON membership_role (membership_id);
CREATE INDEX idx_role_permission_role ON role_permission (role_id);

-- Permissions catalog
INSERT INTO permission (code, description) VALUES
    ('organization.read', 'Read organization'),
    ('organization.update', 'Update organization'),
    ('users.read', 'Read users'),
    ('users.create', 'Create users'),
    ('users.update', 'Update users'),
    ('users.disable', 'Disable users'),
    ('members.read', 'Read organization members'),
    ('members.manage', 'Manage organization members and roles'),
    ('wallet.read', 'Read wallet'),
    ('wallet.transfer', 'Transfer from wallet'),
    ('transactions.read', 'Read transactions'),
    ('transactions.create', 'Create transactions'),
    ('pix.read', 'Read Pix'),
    ('pix.create', 'Create Pix'),
    ('pix.transfer', 'Pix transfer'),
    ('beneficiaries.read', 'Read beneficiaries'),
    ('beneficiaries.create', 'Create beneficiaries'),
    ('beneficiaries.update', 'Update beneficiaries'),
    ('beneficiaries.delete', 'Delete beneficiaries'),
    ('audit.read', 'Read audit log'),
    ('limits.read', 'Read limits'),
    ('limits.manage', 'Manage limits'),
    ('approval.read', 'Read approvals'),
    ('approval.create', 'Create approval requests'),
    ('approval.approve', 'Approve requests'),
    ('approval.reject', 'Reject requests');

INSERT INTO role (code, description) VALUES
    ('OWNER', 'Full access to the organization'),
    ('ADMIN', 'Administer organization, users and members'),
    ('FINANCE', 'Financial operations'),
    ('EMPLOYEE', 'Limited operational access'),
    ('AUDITOR', 'Read-only access');

-- OWNER: all permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.code = 'OWNER';

-- ADMIN
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code IN (
    'organization.read', 'organization.update',
    'users.read', 'users.create', 'users.update', 'users.disable',
    'members.read', 'members.manage',
    'wallet.read',
    'transactions.read',
    'pix.read',
    'beneficiaries.read', 'beneficiaries.create', 'beneficiaries.update', 'beneficiaries.delete',
    'audit.read',
    'limits.read', 'limits.manage',
    'approval.read'
)
WHERE r.code = 'ADMIN';

-- FINANCE
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'wallet.read', 'wallet.transfer',
    'transactions.read', 'transactions.create',
    'pix.read', 'pix.create', 'pix.transfer',
    'beneficiaries.read', 'beneficiaries.create', 'beneficiaries.update', 'beneficiaries.delete',
    'limits.read',
    'approval.read', 'approval.create'
)
WHERE r.code = 'FINANCE';

-- EMPLOYEE
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code IN (
    'organization.read',
    'wallet.read',
    'transactions.read',
    'pix.read',
    'beneficiaries.read'
)
WHERE r.code = 'EMPLOYEE';

-- AUDITOR: all *.read
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
JOIN permission p ON p.code LIKE '%.read'
WHERE r.code = 'AUDITOR';
