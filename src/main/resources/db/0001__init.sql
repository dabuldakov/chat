-- =====================================================
-- 1. Таблица пользователей (BIGINT как внутренний ID)
-- =====================================================
CREATE TABLE users (
                       user_id BIGSERIAL PRIMARY KEY,           -- Внутренний ID для JOIN и партиций
                       user_uuid UUID UNIQUE DEFAULT gen_random_uuid(), -- Внешний ID для API
                       username VARCHAR(50) UNIQUE NOT NULL,
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_uuid ON users(user_uuid);
CREATE INDEX idx_users_username ON users(username);

-- =====================================================
-- 2. Таблица чатов
-- =====================================================
CREATE TABLE chats (
                       chat_id BIGSERIAL PRIMARY KEY,
                       chat_uuid UUID UNIQUE DEFAULT gen_random_uuid(),
                       chat_type VARCHAR(20) NOT NULL CHECK (chat_type IN ('PRIVATE', 'GROUP')),
                       title VARCHAR(255),
                       created_by BIGINT REFERENCES users(user_id),
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chats_uuid ON chats(chat_uuid);

-- =====================================================
-- 3. Таблица участников
-- =====================================================
CREATE TABLE participants (
                              participant_id BIGSERIAL PRIMARY KEY,
                              chat_id BIGINT NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
                              user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                              joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              last_read_message_id BIGINT,
                              UNIQUE(chat_id, user_id)
);

CREATE INDEX idx_participants_chat_id ON participants(chat_id);
CREATE INDEX idx_participants_user_id ON participants(user_id);

-- =====================================================
-- 4. Таблица сообщений с RANGE партиционированием по chat_id
-- =====================================================
CREATE TABLE messages (
                          message_id BIGSERIAL,
                          chat_id BIGINT NOT NULL,
                          sender_id BIGINT NOT NULL,
                          message_text TEXT NOT NULL,
                          message_type VARCHAR(20) DEFAULT 'TEXT',
                          file_url VARCHAR(500),
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          is_deleted BOOLEAN DEFAULT FALSE,
                          PRIMARY KEY (message_id, chat_id)  -- chat_id в PK для партиционирования
) PARTITION BY RANGE (chat_id);  -- RANGE партиционирование! Быстрее чем HASH

-- Создаем партиции по диапазонам chat_id
-- Например, чаты 1-10000 в одной партиции, 10001-20000 в другой, и т.д.
DO $$
DECLARE
i INTEGER;
    start_id BIGINT;
    end_id BIGINT;
BEGIN
FOR i IN 0..15 LOOP
        start_id := (i * 100000) + 1;
        end_id := ((i + 1) * 100000);
EXECUTE format('
            CREATE TABLE messages_partition_%s PARTITION OF messages
            FOR VALUES FROM (%s) TO (%s)
        ', i, start_id, end_id);
END LOOP;
END $$;

-- Индексы на партициях
CREATE INDEX idx_messages_chat_id_created_at ON messages(chat_id, created_at DESC);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);