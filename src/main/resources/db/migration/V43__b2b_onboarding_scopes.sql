-- B2B Asaas onboarding scopes (OAuth client_credentials)

INSERT INTO permission (code, description) VALUES
    ('onboarding.read', 'Read Asaas financial onboarding status'),
    ('onboarding.submit', 'Submit Asaas financial onboarding / create subaccount')
ON CONFLICT (code) DO NOTHING;
