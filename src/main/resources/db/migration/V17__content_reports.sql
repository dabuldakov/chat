-- Жалобы пользователей на пользовательский контент (UGC).
-- Требование Google Play: механизм сообщений о нежелательном контенте.
CREATE TABLE content_reports (
    report_id       BIGSERIAL PRIMARY KEY,
    report_uuid     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    reporter_id     BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    content_type    VARCHAR(20)  NOT NULL,
    content_uuid    UUID         NOT NULL,
    target_user_id  BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    reason          VARCHAR(20)  NOT NULL,
    comment         VARCHAR(1000),
    content_preview VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP,
    reviewed_by     BIGINT       REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT uq_content_reports_reporter_content
        UNIQUE (reporter_id, content_type, content_uuid)
);

CREATE INDEX idx_content_reports_status_created
    ON content_reports (status, created_at DESC);

CREATE INDEX idx_content_reports_content
    ON content_reports (content_type, content_uuid);

CREATE INDEX idx_content_reports_target
    ON content_reports (target_user_id);
