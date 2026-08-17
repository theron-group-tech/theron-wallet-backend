CREATE TABLE notification (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES app_user (id),
    organization_id UUID         REFERENCES organization (id),
    type            VARCHAR(40)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    message         VARCHAR(512) NOT NULL,
    data            JSONB,
    resource_id     UUID,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_notification_type CHECK (type IN (
        'PIX_RECEIVED',
        'PIX_SENT',
        'TRANSFER_RECEIVED',
        'TRANSFER_SENT',
        'TRANSFER_APPROVAL_REQUIRED',
        'TRANSFER_APPROVED',
        'TRANSFER_REJECTED',
        'LOGIN_NEW_DEVICE',
        'PASSWORD_CHANGED'
    ))
);

CREATE INDEX idx_notification_user_created
    ON notification (user_id, created_at DESC);

CREATE INDEX idx_notification_user_unread
    ON notification (user_id)
    WHERE read_at IS NULL;

CREATE UNIQUE INDEX uq_notification_user_type_resource
    ON notification (user_id, type, resource_id)
    WHERE resource_id IS NOT NULL;
