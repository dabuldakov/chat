-- =====================================================
-- ТАБЛИЦА chats (чаты)
-- created_by НЕ NOT NULL: FK объявлен ON DELETE SET NULL,
-- поэтому допускаем NULL после удаления создателя.
-- =====================================================

CREATE TABLE chats (
    chat_id               BIGSERIAL PRIMARY KEY,
    chat_uuid             UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    chat_type             VARCHAR(20)  NOT NULL
        CHECK (chat_type IN ('PRIVATE', 'GROUP', 'CHANNEL')),
    title                 VARCHAR(255),
    description           TEXT,
    avatar_url            VARCHAR(500),
    created_by            BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    is_private            BOOLEAN      DEFAULT FALSE,
    is_archived           BOOLEAN      DEFAULT FALSE,
    last_message_id       BIGINT,
    last_message_text     TEXT,
    last_message_sender_id BIGINT,
    message_count         BIGINT       DEFAULT 0,
    created_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT       DEFAULT 0
);

CREATE INDEX idx_chats_chat_type        ON chats(chat_type);
CREATE INDEX idx_chats_created_by       ON chats(created_by);
CREATE INDEX idx_chats_updated_at       ON chats(updated_at DESC);
CREATE INDEX idx_chats_is_archived      ON chats(is_archived);
CREATE INDEX idx_chats_type_updated     ON chats(chat_type, updated_at DESC);
CREATE INDEX idx_chats_created_updated  ON chats(created_by, updated_at DESC);

CREATE TRIGGER update_chats_updated_at
    BEFORE UPDATE ON chats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
