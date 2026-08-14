-- PENDING_APPROVAL for hold-before-money PIX transfers
ALTER TABLE transaction DROP CONSTRAINT chk_transaction_status;
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_status CHECK (
    status IN (
        'PENDING', 'PENDING_APPROVAL', 'PROCESSING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'
    )
);

ALTER TABLE pix_transaction DROP CONSTRAINT chk_pix_transaction_status;
ALTER TABLE pix_transaction ADD CONSTRAINT chk_pix_transaction_status CHECK (
    status IN (
        'PENDING', 'PENDING_APPROVAL', 'PROCESSING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'
    )
);

-- FINANCE can approve/reject (permissions already seeded in V14)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('approval.approve', 'approval.reject')
WHERE r.code = 'FINANCE'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

CREATE TABLE approval_policy (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id           UUID           NOT NULL REFERENCES account (id),
    organization_id      UUID           NOT NULL REFERENCES organization (id),
    amount_min           NUMERIC(19, 2) NOT NULL,
    amount_max           NUMERIC(19, 2),
    required_approvals   INT            NOT NULL,
    status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_approval_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_approval_policy_required CHECK (required_approvals >= 0),
    CONSTRAINT chk_approval_policy_range CHECK (
        amount_min >= 0
        AND (amount_max IS NULL OR amount_max >= amount_min)
    )
);

CREATE INDEX idx_approval_policy_account ON approval_policy (account_id);

CREATE TABLE approval_request (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id       UUID           NOT NULL UNIQUE REFERENCES transaction (id),
    account_id           UUID           NOT NULL REFERENCES account (id),
    organization_id      UUID           NOT NULL REFERENCES organization (id),
    requested_by_user_id UUID           NOT NULL REFERENCES app_user (id),
    required_approvals   INT            NOT NULL,
    approved_count       INT            NOT NULL DEFAULT 0,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    expires_at           TIMESTAMP      NOT NULL,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_approval_request_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT chk_approval_request_required CHECK (required_approvals >= 1),
    CONSTRAINT chk_approval_request_count CHECK (approved_count >= 0)
);

CREATE INDEX idx_approval_request_account ON approval_request (account_id);
CREATE INDEX idx_approval_request_status ON approval_request (status);

CREATE TABLE approval_action (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id  UUID         NOT NULL REFERENCES approval_request (id),
    actor_user_id        UUID         NOT NULL REFERENCES app_user (id),
    action               VARCHAR(20)  NOT NULL,
    comment              VARCHAR(500),
    idempotency_key      VARCHAR(120),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_approval_action_type CHECK (action IN ('APPROVE', 'REJECT', 'CANCEL')),
    CONSTRAINT uq_approval_action_actor UNIQUE (approval_request_id, actor_user_id, action),
    CONSTRAINT uq_approval_action_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_approval_action_request ON approval_action (approval_request_id);
