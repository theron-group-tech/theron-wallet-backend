-- Organization: Theron multi-tenant company (independent of Asaas subaccount)
CREATE TABLE organization (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name    VARCHAR(255) NOT NULL,
    trade_name    VARCHAR(255),
    document      VARCHAR(20)  NOT NULL,
    document_type VARCHAR(10)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_organization_document UNIQUE (document),
    CONSTRAINT chk_organization_document_type CHECK (document_type IN ('CPF', 'CNPJ')),
    CONSTRAINT chk_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLOCKED'))
);

CREATE INDEX idx_organization_status ON organization (status);
CREATE INDEX idx_organization_created_at ON organization (created_at);
