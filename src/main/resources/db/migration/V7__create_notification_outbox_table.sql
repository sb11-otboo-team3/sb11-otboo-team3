CREATE TABLE notification_outbox(
    id           UUID PRIMARY KEY,
    receiver_id  UUID         NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    level        VARCHAR(20)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    retry_count  INTEGER      NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_notification_outbox_level
        CHECK (level IN ('INFO', 'WARNING', 'ERROR')),

    CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),

    CONSTRAINT chk_notification_outbox_retry_count
        CHECK (retry_count >= 0),

    CONSTRAINT chk_notification_outbox_title_not_blank
        CHECK (btrim(title) <> ''),

    CONSTRAINT chk_notification_outbox_content_not_blank
        CHECK (btrim(content) <> '')
);

CREATE INDEX idx_notification_outbox_status_created_at
    ON notification_outbox (status, created_at);

CREATE INDEX idx_notification_outbox_receiver_id
    ON notification_outbox (receiver_id);