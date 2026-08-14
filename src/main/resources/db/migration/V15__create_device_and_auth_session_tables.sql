-- Product auth devices and refresh sessions (Module 4)
CREATE TABLE device (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES app_user (id),
    client_device_id VARCHAR(128) NOT NULL,
    device_name      VARCHAR(255),
    platform         VARCHAR(64),
    ip               VARCHAR(45),
    user_agent       VARCHAR(512),
    last_seen_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at       TIMESTAMP,

    CONSTRAINT uq_device_user_client UNIQUE (user_id, client_device_id)
);

CREATE INDEX idx_device_user ON device (user_id);

CREATE TABLE auth_session (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES app_user (id),
    device_id          UUID        NOT NULL REFERENCES device (id),
    refresh_token_hash VARCHAR(64) NOT NULL,
    expires_at         TIMESTAMP   NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT NOW(),
    last_used_at       TIMESTAMP,
    revoked_at         TIMESTAMP,
    replaced_at        TIMESTAMP,

    CONSTRAINT uq_auth_session_refresh_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_auth_session_user_revoked ON auth_session (user_id, revoked_at);
CREATE INDEX idx_auth_session_device ON auth_session (device_id);
