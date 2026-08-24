CREATE TABLE file_deletion_outbox(
                                     id                 UUID PRIMARY KEY,
                                     object_key         TEXT         NOT NULL,
                                     status             VARCHAR(20)  NOT NULL,
                                     retry_count        INTEGER      NOT NULL DEFAULT 0,
                                     last_error_message TEXT NULL,
                                     deleted_at         TIMESTAMPTZ NULL,
                                     created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                     updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                     CONSTRAINT chk_file_deletion_outbox_status
                                         CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
                                     CONSTRAINT chk_file_deletion_outbox_retry_count
                                         CHECK (retry_count >= 0),
                                     CONSTRAINT chk_file_deletion_outbox_object_key_not_blank
                                         CHECK (btrim(object_key) <> '')
);
CREATE INDEX idx_file_deletion_outbox_status_created_at
    ON file_deletion_outbox (status, created_at);