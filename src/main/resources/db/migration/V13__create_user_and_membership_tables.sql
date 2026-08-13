-- Product users (tenant employees) — separate from admin_users
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP,

    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_app_user_status ON app_user (status);

-- N:N membership between Organization and User
CREATE TABLE organization_membership (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organization (id),
    user_id         UUID        NOT NULL REFERENCES app_user (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_organization_membership_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT chk_organization_membership_status CHECK (status IN ('ACTIVE', 'INVITED', 'SUSPENDED', 'REMOVED'))
);

CREATE INDEX idx_organization_membership_org_status ON organization_membership (organization_id, status);
CREATE INDEX idx_organization_membership_user ON organization_membership (user_id);
