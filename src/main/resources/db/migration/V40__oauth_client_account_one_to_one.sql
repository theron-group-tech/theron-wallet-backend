-- Enforce 1 OAuth client ↔ 1 Account (credentials identify a specific account within an organization).

-- Collapse any legacy multi-account / shared-account bindings.
DELETE FROM oauth_client_account a
USING (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY client_id ORDER BY id) AS rn
        FROM oauth_client_account
    ) ranked
    WHERE ranked.rn > 1
) dup
WHERE a.id = dup.id;

DELETE FROM oauth_client_account a
USING (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY account_id ORDER BY id) AS rn
        FROM oauth_client_account
    ) ranked
    WHERE ranked.rn > 1
) dup
WHERE a.id = dup.id;

CREATE UNIQUE INDEX uq_oauth_client_account_one_client
    ON oauth_client_account (client_id);

CREATE UNIQUE INDEX uq_oauth_client_account_one_account
    ON oauth_client_account (account_id);
