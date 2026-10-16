-- =====================================================
-- GIF: каталог, кэш поиска и использование (Мои/Этот чат).
--
-- Каталог хранит метаданные выбранных из провайдера (Klipy) гифок и,
-- опционально, ссылку на зеркало в MinIO (object_key/public_url), чтобы
-- отдавать медиа со своей инфраструктуры.
-- =====================================================

-- GIF становится отдельным типом сообщения и вложения.
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_message_type_check
    CHECK (message_type IN ('TEXT', 'IMAGE', 'VIDEO', 'GIF', 'AUDIO', 'FILE',
                            'LOCATION', 'CONTACT', 'SYSTEM', 'DELETE'));

ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_type_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_type_check
    CHECK (type IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT', 'GIF', 'OTHER'));

CREATE TABLE gif_catalog (
    gif_id          BIGSERIAL PRIMARY KEY,
    gif_uuid        UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    provider        VARCHAR(32)   NOT NULL,
    provider_gif_id VARCHAR(255)  NOT NULL,
    title           VARCHAR(500),
    source_url      VARCHAR(1000) NOT NULL,
    preview_url     VARCHAR(1000),
    width           INTEGER,
    height          INTEGER,
    rating          VARCHAR(16),
    object_key      VARCHAR(500),
    public_url      VARCHAR(1000),
    mirrored        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP,
    use_count       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_gif_provider UNIQUE (provider, provider_gif_id)
);

CREATE INDEX idx_gif_catalog_provider ON gif_catalog(provider);
CREATE INDEX idx_gif_catalog_last_used ON gif_catalog(last_used_at DESC);

-- «Мои гифки»: последние использованные пользователем (порядок по времени).
CREATE TABLE gif_user_usage (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT    NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    gif_id       BIGINT    NOT NULL REFERENCES gif_catalog(gif_id) ON DELETE CASCADE,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    use_count    BIGINT    NOT NULL DEFAULT 1,
    CONSTRAINT uq_gif_user_usage UNIQUE (user_id, gif_id)
);

CREATE INDEX idx_gif_user_usage_user ON gif_user_usage(user_id, last_used_at DESC);

-- «Гифки этого чата»: последние использованные в чате (порядок по времени).
CREATE TABLE gif_chat_usage (
    id           BIGSERIAL PRIMARY KEY,
    chat_id      BIGINT    NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
    gif_id       BIGINT    NOT NULL REFERENCES gif_catalog(gif_id) ON DELETE CASCADE,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    use_count    BIGINT    NOT NULL DEFAULT 1,
    CONSTRAINT uq_gif_chat_usage UNIQUE (chat_id, gif_id)
);

CREATE INDEX idx_gif_chat_usage_chat ON gif_chat_usage(chat_id, last_used_at DESC);
