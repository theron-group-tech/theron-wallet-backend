-- OAuth 2.0 Client Credentials for B2B (M2M) API access.
-- client_secret_hash is SHA-256 hex of a high-entropy server-generated secret.

CREATE TABLE oauth_client (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organization(id),
    client_id           VARCHAR(64) NOT NULL,
    client_secret_hash  VARCHAR(64) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    environment         VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at          TIMESTAMP,
    last_used_at        TIMESTAMP,
    CONSTRAINT uq_oauth_client_client_id UNIQUE (client_id),
    CONSTRAINT chk_oauth_client_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_oauth_client_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION'))
);

CREATE INDEX idx_oauth_client_organization ON oauth_client(organization_id);
CREATE INDEX idx_oauth_client_status ON oauth_client(status);

CREATE TABLE oauth_client_scope (
    id          UUID PRIMARY KEY,
    client_id   UUID NOT NULL REFERENCES oauth_client(id) ON DELETE CASCADE,
    scope       VARCHAR(100) NOT NULL,
    CONSTRAINT uq_oauth_client_scope UNIQUE (client_id, scope)
);

CREATE INDEX idx_oauth_client_scope_client ON oauth_client_scope(client_id);

CREATE TABLE oauth_client_account (
    id          UUID PRIMARY KEY,
    client_id   UUID NOT NULL REFERENCES oauth_client(id) ON DELETE CASCADE,
    account_id  UUID NOT NULL REFERENCES account(id),
    CONSTRAINT uq_oauth_client_account UNIQUE (client_id, account_id)
);

CREATE INDEX idx_oauth_client_account_client ON oauth_client_account(client_id);
CREATE INDEX idx_oauth_client_account_account ON oauth_client_account(account_id);

CREATE TABLE oauth_client_audit (
    id              UUID PRIMARY KEY,
    client_id       UUID NOT NULL REFERENCES oauth_client(id) ON DELETE CASCADE,
    action          VARCHAR(40) NOT NULL,
    actor_admin_id  UUID,
    detail          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_oauth_client_audit_client ON oauth_client_audit(client_id);
