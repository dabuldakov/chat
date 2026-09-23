-- =====================================================
-- ТАБЛИЦА messages (сообщения) с RANGE-партиционированием по created_at.
--
-- RANGE по времени выбран вместо HASH(chat_id):
--   * история вечная, но партиции обслуживаются отдельно (vacuum, индексы);
--   * pruning для запросов по диапазону времени (sync/after/before);
--   * при необходимости старые партиции можно вынести на дешёвый storage
--     (ALTER TABLE ... SET TABLESPACE) или отключить без удаления.
--
-- message_id — единая последовательность, глобально уникален.
-- PK (message_id, created_at): ключ партиционирования обязан входить в PK.
-- =====================================================

CREATE TABLE messages (
    message_id                BIGSERIAL   NOT NULL,
    created_at                TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chat_id                   BIGINT      NOT NULL,
    message_uuid              UUID        DEFAULT gen_random_uuid(),
    sender_id                 BIGINT      NOT NULL,
    message_text              TEXT,
    message_type              VARCHAR(20) DEFAULT 'TEXT'
        CHECK (message_type IN ('TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE',
                                'LOCATION', 'CONTACT', 'SYSTEM', 'DELETE')),
    reply_to_message_id       BIGINT,
    forwarded_from_message_id BIGINT,
    forwarded_from_user_id    BIGINT,
    is_edited                 BOOLEAN     DEFAULT FALSE,
    edit_history              TEXT,
    is_deleted                BOOLEAN     DEFAULT FALSE,
    is_pinned                 BOOLEAN     DEFAULT FALSE,
    deleted_at                TIMESTAMP,
    deleted_by                BIGINT,
    mentioned_users           TEXT        DEFAULT '[]',
    has_attachments           BOOLEAN     DEFAULT FALSE,
    metadata                  TEXT        DEFAULT '{}',
    updated_at                TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    version                   BIGINT      DEFAULT 0,
    PRIMARY KEY (message_id, created_at),
    CONSTRAINT fk_messages_chat   FOREIGN KEY (chat_id)   REFERENCES chats(chat_id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE
) PARTITION BY RANGE (created_at);

-- Страховочная партиция для сообщений вне покрытого окна.
-- Планировщик создаёт месячные партиции заранее, поэтому она должна
-- оставаться пустой; если в неё что-то попало — нужно создать нужную
-- партицию (или перенести строки) до следующего auto-create.
CREATE TABLE messages_default PARTITION OF messages DEFAULT;

-- =====================================================
-- Автосоздание месячных партиций.
-- Идемпотентно, вызывается из миграции и из планировщика приложения.
-- =====================================================

CREATE OR REPLACE FUNCTION ensure_message_partitions(months_ahead int DEFAULT 6)
RETURNS void AS $$
DECLARE
    base date := date_trunc('month', CURRENT_DATE)::date;
    i    int;
    m    date;
BEGIN
    FOR i IN -1 .. months_ahead LOOP
        m := (base + (i || ' months')::interval)::date;
        IF to_regclass(format('public.messages_%s', to_char(m, 'YYYY_MM'))) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE public.messages_%s PARTITION OF public.messages
                 FOR VALUES FROM (%L) TO (%L)',
                to_char(m, 'YYYY_MM'), m, (m + interval '1 month')::date);
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT ensure_message_partitions(6);

-- =====================================================
-- ИНДЕКСЫ (на партиционированной таблице каскадируются на все
-- существующие и будущие партиции)
-- =====================================================

-- message_id покрыт PK-префиксом; отдельный индекс не нужен.
CREATE INDEX idx_messages_chat_message   ON messages(chat_id, message_id DESC);
CREATE INDEX idx_messages_chat_created   ON messages(chat_id, created_at DESC);
CREATE INDEX idx_messages_chat_deleted   ON messages(chat_id, is_deleted, created_at DESC);
CREATE INDEX idx_messages_chat_pinned    ON messages(chat_id, is_pinned, created_at DESC);
CREATE INDEX idx_messages_sender_id      ON messages(sender_id);
CREATE INDEX idx_messages_message_uuid   ON messages(message_uuid);
CREATE INDEX idx_messages_reply_to       ON messages(reply_to_message_id);
CREATE INDEX idx_messages_is_deleted     ON messages(is_deleted);
CREATE INDEX idx_messages_is_pinned      ON messages(is_pinned);
CREATE INDEX idx_messages_message_type   ON messages(message_type);
CREATE INDEX idx_messages_created_at     ON messages(created_at DESC);
CREATE INDEX idx_messages_text_gin ON messages
    USING GIN (to_tsvector('russian', COALESCE(message_text, '')));

CREATE TRIGGER update_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
