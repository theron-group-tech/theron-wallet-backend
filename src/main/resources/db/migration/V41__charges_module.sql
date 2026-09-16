-- Charges module: billing customers, charges, installments, splits, anticipations
-- Permissions for B2B OAuth scopes + product roles

INSERT INTO permission (code, description) VALUES
    ('charges.read', 'Read billing charges'),
    ('charges.create', 'Create billing charges'),
    ('charges.cancel', 'Cancel billing charges'),
    ('anticipations.read', 'Read receivable anticipations'),
    ('anticipations.create', 'Simulate and create receivable anticipations')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
CROSS JOIN permission p
WHERE r.code = 'OWNER'
  AND p.code IN (
    'charges.read', 'charges.create', 'charges.cancel',
    'anticipations.read', 'anticipations.create'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
CROSS JOIN permission p
WHERE r.code = 'FINANCE'
  AND p.code IN (
    'charges.read', 'charges.create', 'charges.cancel',
    'anticipations.read', 'anticipations.create'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE billing_customer (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organization(id),
    account_id          UUID NOT NULL REFERENCES account(id),
    asaas_customer_id   VARCHAR(50),
    name                VARCHAR(255) NOT NULL,
    cpf_cnpj            VARCHAR(14) NOT NULL,
    email               VARCHAR(255),
    phone               VARCHAR(20),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_billing_customer_account_cpf UNIQUE (account_id, cpf_cnpj)
);

CREATE INDEX idx_billing_customer_org_account ON billing_customer(organization_id, account_id);
CREATE INDEX idx_billing_customer_asaas ON billing_customer(asaas_customer_id);

CREATE TABLE charge (
    id                      UUID PRIMARY KEY,
    organization_id         UUID NOT NULL REFERENCES organization(id),
    account_id              UUID NOT NULL REFERENCES account(id),
    subaccount_id           UUID REFERENCES subaccount(id),
    billing_customer_id     UUID NOT NULL REFERENCES billing_customer(id),
    transaction_id          UUID REFERENCES transaction(id),
    asaas_payment_id        VARCHAR(50),
    billing_type            VARCHAR(20) NOT NULL,
    value                   NUMERIC(19, 2) NOT NULL,
    net_value               NUMERIC(19, 2),
    description             VARCHAR(500),
    external_reference      VARCHAR(100),
    due_date                DATE NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    installment_count       INT NOT NULL DEFAULT 1,
    invoice_url             VARCHAR(500),
    bank_slip_url           VARCHAR(500),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_charge_billing_type CHECK (billing_type IN ('BOLETO', 'CREDIT_CARD', 'PIX')),
    CONSTRAINT uq_charge_asaas_payment UNIQUE (asaas_payment_id)
);

CREATE UNIQUE INDEX uq_charge_account_external_ref
    ON charge (account_id, external_reference)
    WHERE external_reference IS NOT NULL;

CREATE INDEX idx_charge_org_account ON charge(organization_id, account_id);
CREATE INDEX idx_charge_status ON charge(status);
CREATE INDEX idx_charge_external_reference ON charge(external_reference);

CREATE TABLE charge_installment (
    id                  UUID PRIMARY KEY,
    charge_id           UUID NOT NULL REFERENCES charge(id) ON DELETE CASCADE,
    installment_number  INT NOT NULL,
    value               NUMERIC(19, 2) NOT NULL,
    due_date            DATE,
    asaas_payment_id    VARCHAR(50),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_charge_installment UNIQUE (charge_id, installment_number)
);

CREATE INDEX idx_charge_installment_charge ON charge_installment(charge_id);

CREATE TABLE charge_split (
    id                  UUID PRIMARY KEY,
    charge_id           UUID NOT NULL REFERENCES charge(id) ON DELETE CASCADE,
    wallet_id           VARCHAR(100) NOT NULL,
    percentual_value    NUMERIC(10, 4),
    fixed_value         NUMERIC(19, 2),
    role                VARCHAR(20) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_charge_split_role CHECK (role IN ('PLATFORM', 'COUNTERPARTY'))
);

CREATE INDEX idx_charge_split_charge ON charge_split(charge_id);

CREATE TABLE receivable_anticipation (
    id                      UUID PRIMARY KEY,
    organization_id         UUID NOT NULL REFERENCES organization(id),
    account_id              UUID NOT NULL REFERENCES account(id),
    asaas_anticipation_id   VARCHAR(50),
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_value         NUMERIC(19, 2),
    net_value               NUMERIC(19, 2),
    fee_value               NUMERIC(19, 2),
    payment_ids_json        TEXT,
    simulation_json         TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_receivable_anticipation_org_account
    ON receivable_anticipation(organization_id, account_id);
CREATE INDEX idx_receivable_anticipation_asaas
    ON receivable_anticipation(asaas_anticipation_id);
