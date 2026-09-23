-- =====================================================
-- ТАБЛИЦА attachments (вложения)
--
-- message_id может быть NULL (файл загружен, но ещё не привязан).
-- FK на messages НЕТ: messages RANGE-партиционирована по created_at,
-- поэтому (chat_id, message_id) не является уникальным ключом и на него
-- нельзя сослаться. Целостность attachments.message_id обеспечивается
-- на уровне приложения (как и было).
-- =====================================================

CREATE TABLE attachments (
    attachment_id   BIGSERIAL PRIMARY KEY,
    attachment_uuid UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    message_id      BIGINT,
    chat_id         BIGINT       NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
    uploader_id     BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500),
    file_size       BIGINT,
    mime_type       VARCHAR(100),
    width           INTEGER,
    height          INTEGER,
    duration        INTEGER,
    type            VARCHAR(20)  DEFAULT 'OTHER'
        CHECK (type IN ('IMAGE', 'VIDEO', 'AUDIO', 'DOCUMENT', 'OTHER')),
    is_compressed   BOOLEAN      DEFAULT FALSE,
    metadata        TEXT         DEFAULT '{}',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT       DEFAULT 0
);

CREATE INDEX idx_attachments_message_id   ON attachments(message_id);
CREATE INDEX idx_attachments_uploader_id  ON attachments(uploader_id);
CREATE INDEX idx_attachments_type         ON attachments(type);
CREATE INDEX idx_attachments_created_at   ON attachments(created_at DESC);
CREATE INDEX idx_attachments_chat_created ON attachments(chat_id, created_at DESC);

CREATE TRIGGER update_attachments_updated_at
    BEFORE UPDATE ON attachments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
