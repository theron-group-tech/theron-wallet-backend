-- Asaas financial onboarding is an organization-level administrative operation.
-- OWNER may read and submit onboarding; FINANCE/EMPLOYEE do not receive these permissions.

INSERT INTO permission (code, description) VALUES
    ('onboarding.read', 'Read Asaas onboarding state'),
    ('onboarding.submit', 'Submit Asaas onboarding and create Asaas subaccount')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code = 'OWNER'
  AND p.code IN ('onboarding.read', 'onboarding.submit')
ON CONFLICT DO NOTHING;
