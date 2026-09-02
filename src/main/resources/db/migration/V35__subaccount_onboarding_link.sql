ALTER TABLE subaccount
    ADD COLUMN person_type VARCHAR(20),
    ADD COLUMN onboarding_id UUID REFERENCES asaas_onboarding(id),
    ADD COLUMN legacy_auto_provisioned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_subaccount_onboarding ON subaccount(onboarding_id);

-- Existing rows were auto-provisioned before self-service onboarding.
UPDATE subaccount SET legacy_auto_provisioned = TRUE WHERE account_id IS NOT NULL;
