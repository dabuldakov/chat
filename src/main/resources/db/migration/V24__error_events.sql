-- Диагностика клиента: ошибки/краши, присланные при старте приложения.
CREATE TABLE error_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    user_id      BIGINT,
    app_version  VARCHAR(32)  NOT NULL,
    version_code INTEGER,
    build_type   VARCHAR(16),
    device       VARCHAR(200),
    os           VARCHAR(64),
    level        VARCHAR(16)  NOT NULL,
    tag          VARCHAR(200),
    message      TEXT,
    stack        TEXT,
    event_time   TIMESTAMP,
    received_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_error_events_version_time ON error_events (app_version, received_at DESC);
CREATE INDEX idx_error_events_user ON error_events (user_id);
CREATE INDEX idx_error_events_level ON error_events (level, received_at DESC);
