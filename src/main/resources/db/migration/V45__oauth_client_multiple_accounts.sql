-- The integration API key represents an organization integration.
-- One Account may still have at most one API key, but one API key may manage
-- the Owner account plus Finance/Employee accounts created through the integration.
DROP INDEX IF EXISTS uq_oauth_client_account_one_client;
