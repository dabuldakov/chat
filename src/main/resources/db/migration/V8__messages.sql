-- =====================================================
-- ТАБЛИЦА messages (сообщения с партиционированием)
-- =====================================================

-- Создаем партиционированную таблицу
CREATE TABLE IF NOT EXISTS messages (
                                        message_id BIGSERIAL,
                                        message_uuid UUID DEFAULT gen_random_uuid(),
    chat_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message_text TEXT,
    message_type VARCHAR(20) DEFAULT 'TEXT',
    reply_to_message_id BIGINT,
    forwarded_from_message_id BIGINT,
    forwarded_from_user_id BIGINT,
    is_edited BOOLEAN DEFAULT FALSE,
    edit_history TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    is_pinned BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    mentioned_users TEXT DEFAULT '[]',
    has_attachments BOOLEAN DEFAULT FALSE,
    metadata TEXT DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    PRIMARY KEY (message_id, chat_id)
    ) PARTITION BY HASH (chat_id);

-- =====================================================
-- СОЗДАНИЕ ПАРТИЦИЙ (16 партиций)
-- =====================================================

DO $$
DECLARE
i INTEGER;
BEGIN
FOR i IN 0..15 LOOP
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS messages_partition_%s PARTITION OF messages
            FOR VALUES WITH (MODULUS 16, REMAINDER %s)
        ', i, i);
END LOOP;
END $$;

-- =====================================================
-- ИНДЕКСЫ
-- =====================================================

-- Основные индексы (создаются на партициях автоматически, но для явного указания)
CREATE INDEX IF NOT EXISTS idx_messages_chat_id_created_at ON messages(chat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_message_uuid ON messages(message_uuid);
CREATE INDEX IF NOT EXISTS idx_messages_reply_to ON messages(reply_to_message_id);
CREATE INDEX IF NOT EXISTS idx_messages_is_deleted ON messages(is_deleted);
CREATE INDEX IF NOT EXISTS idx_messages_is_pinned ON messages(is_pinned);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_message_type ON messages(message_type);

-- Составные индексы
CREATE INDEX IF NOT EXISTS idx_messages_chat_deleted ON messages(chat_id, is_deleted, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_chat_pinned ON messages(chat_id, is_pinned, created_at DESC);

-- =====================================================
-- ТРИГГЕРЫ
-- =====================================================

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_messages_updated_at ON messages;
CREATE TRIGGER update_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦЕ И ПОЛЯМ
-- =====================================================

COMMENT ON TABLE messages IS 'Таблица сообщений с партиционированием по HASH(chat_id)';
COMMENT ON COLUMN messages.message_id IS 'Внутренний ID сообщения';
COMMENT ON COLUMN messages.message_uuid IS 'Внешний UUID сообщения для API';
COMMENT ON COLUMN messages.chat_id IS 'ID чата (используется для партиционирования)';
COMMENT ON COLUMN messages.sender_id IS 'ID отправителя сообщения';
COMMENT ON COLUMN messages.message_text IS 'Текст сообщения';
COMMENT ON COLUMN messages.message_type IS 'Тип сообщения: TEXT, IMAGE, VIDEO, AUDIO, FILE, LOCATION, CONTACT, SYSTEM, DELETE';
COMMENT ON COLUMN messages.reply_to_message_id IS 'ID сообщения, на которое отвечают';
COMMENT ON COLUMN messages.forwarded_from_message_id IS 'ID оригинального сообщения при пересылке';
COMMENT ON COLUMN messages.forwarded_from_user_id IS 'ID оригинального отправителя при пересылке';
COMMENT ON COLUMN messages.is_edited IS 'Флаг, было ли сообщение отредактировано';
COMMENT ON COLUMN messages.is_deleted IS 'Флаг мягкого удаления';
COMMENT ON COLUMN messages.is_pinned IS 'Флаг закрепленного сообщения';
COMMENT ON COLUMN messages.has_attachments IS 'Флаг наличия вложений';
COMMENT ON COLUMN messages.metadata IS 'Дополнительные метаданные в формате JSON';

-- =====================================================
-- ПРОВЕРКА ПАРТИЦИЙ
-- =====================================================

-- Проверка созданных партиций
SELECT
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE tablename LIKE 'messages_partition_%'
ORDER BY tablename;

-- Проверка размера партиций (после заполнения данными)
/*
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'messages_partition_%'
ORDER BY tablename;
*/