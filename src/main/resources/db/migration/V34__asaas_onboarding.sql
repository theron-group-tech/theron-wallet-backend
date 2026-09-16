CREATE TABLE asaas_onboarding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES app_user(id),
    account_id          UUID NOT NULL UNIQUE REFERENCES account(id),
    person_type         VARCHAR(20),
    current_step        VARCHAR(40) NOT NULL DEFAULT 'ACCOUNT_TYPE',
    status              VARCHAR(40) NOT NULL DEFAULT 'NOT_STARTED',
    payload_json        TEXT,
    last_error_code     VARCHAR(80),
    last_error_message  VARCHAR(500),
    idempotency_key     VARCHAR(64),
    submit_attempts     INT NOT NULL DEFAULT 0,
    onboarding_url      VARCHAR(1024),
    asaas_account_id    VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asaas_onboarding_user ON asaas_onboarding(user_id);
CREATE INDEX idx_asaas_onboarding_status ON asaas_onboarding(status);
