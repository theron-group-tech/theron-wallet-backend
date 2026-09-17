-- Theron API Key fields on oauth_client (API Key = OauthClient evolution)
-- V42 b2b onboarding scopes already applied on theron_wallet_test; V43 = platform_pix_keys

ALTER TABLE oauth_client
    ADD COLUMN IF NOT EXISTS api_key_prefix VARCHAR(48),
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_oauth_client_api_key_prefix
    ON oauth_client (api_key_prefix)
    WHERE api_key_prefix IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_oauth_client_secret_hash
    ON oauth_client (client_secret_hash);

-- Developer API Keys management (OWNER only)
INSERT INTO permission (code, description) VALUES
    ('developer.api_keys.manage', 'Create, list, revoke and rotate Theron API Keys for the organization')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r
CROSS JOIN permission p
WHERE r.code = 'OWNER'
  AND p.code = 'developer.api_keys.manage'
ON CONFLICT (role_id, permission_id) DO NOTHING;
