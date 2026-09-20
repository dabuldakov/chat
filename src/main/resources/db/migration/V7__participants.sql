-- =====================================================
-- ТАБЛИЦА participants (участники чатов)
-- =====================================================

CREATE TABLE IF NOT EXISTS participants (
                                            participant_id BIGSERIAL PRIMARY KEY,
                                            chat_id BIGINT NOT NULL,
                                            user_id BIGINT NOT NULL,
                                            user_uuid UUID NOT NULL,
                                            joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            last_read_message_id BIGINT,
                                            last_read_at TIMESTAMP,
                                            role VARCHAR(20) DEFAULT 'MEMBER',
    nickname VARCHAR(100),
    muted_until TIMESTAMP,
    is_pinned BOOLEAN DEFAULT FALSE,
    notification_settings TEXT DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_participant_chat_user UNIQUE (chat_id, user_id)
    );

-- =====================================================
-- ВНЕШНИЕ КЛЮЧИ
-- =====================================================

-- Связь с чатом
ALTER TABLE participants
DROP CONSTRAINT IF EXISTS fk_participants_chat;
ALTER TABLE participants
    ADD CONSTRAINT fk_participants_chat
        FOREIGN KEY (chat_id) REFERENCES chats(chat_id) ON DELETE CASCADE;

-- Связь с пользователем
ALTER TABLE participants
DROP CONSTRAINT IF EXISTS fk_participants_user;
ALTER TABLE participants
    ADD CONSTRAINT fk_participants_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- =====================================================
-- ИНДЕКСЫ
-- =====================================================

-- Основные индексы
CREATE INDEX IF NOT EXISTS idx_participants_chat_id ON participants(chat_id);
CREATE INDEX IF NOT EXISTS idx_participants_user_id ON participants(user_id);
CREATE INDEX IF NOT EXISTS idx_participants_role ON participants(role);
CREATE INDEX IF NOT EXISTS idx_participants_joined_at ON participants(joined_at DESC);
CREATE INDEX IF NOT EXISTS idx_participants_user_uuid ON participants(user_uuid);

-- Составные индексы для частых запросов
CREATE INDEX IF NOT EXISTS idx_participants_chat_user ON participants(chat_id, user_id);
CREATE INDEX IF NOT EXISTS idx_participants_user_chat ON participants(user_id, chat_id);
CREATE INDEX IF NOT EXISTS idx_participants_chat_role ON participants(chat_id, role);
CREATE INDEX IF NOT EXISTS idx_participants_user_role ON participants(user_id, role);

-- Индекс для поиска по muted_until (для проверки мута)
CREATE INDEX IF NOT EXISTS idx_participants_muted_until ON participants(muted_until)
    WHERE muted_until IS NOT NULL;

-- Индекс для закрепленных чатов
CREATE INDEX IF NOT EXISTS idx_participants_pinned ON participants(user_id, is_pinned)
    WHERE is_pinned = true;

-- Индекс для последнего прочитанного сообщения
CREATE INDEX IF NOT EXISTS idx_participants_last_read ON participants(chat_id, last_read_message_id);

-- =====================================================
-- ОГРАНИЧЕНИЯ (CHECK)
-- =====================================================

-- Проверка допустимых значений роли
ALTER TABLE participants
DROP CONSTRAINT IF EXISTS chk_participants_role;
ALTER TABLE participants
    ADD CONSTRAINT chk_participants_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));

-- Проверка, что user_uuid соответствует user_id (триггерная проверка)
-- Эта проверка выполняется на уровне приложения, но для целостности можно добавить триггер

-- =====================================================
-- ТРИГГЕРЫ
-- =====================================================

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_participants_updated_at ON participants;
CREATE TRIGGER update_participants_updated_at
    BEFORE UPDATE ON participants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Триггер для автоматической установки user_uuid
CREATE OR REPLACE FUNCTION set_participant_user_uuid()
RETURNS TRIGGER AS $$
BEGIN
    -- Устанавливаем user_uuid из таблицы users
SELECT user_uuid INTO NEW.user_uuid
FROM users WHERE user_id = NEW.user_id;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_participants_set_user_uuid ON participants;
CREATE TRIGGER trg_participants_set_user_uuid
    BEFORE INSERT ON participants
    FOR EACH ROW
    EXECUTE FUNCTION set_participant_user_uuid();

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦЕ И ПОЛЯМ
-- =====================================================

COMMENT ON TABLE participants IS 'Участники чатов';
COMMENT ON COLUMN participants.participant_id IS 'Внутренний ID участника';
COMMENT ON COLUMN participants.chat_id IS 'ID чата';
COMMENT ON COLUMN participants.user_id IS 'ID пользователя';
COMMENT ON COLUMN participants.user_uuid IS 'UUID пользователя (денормализованное поле для быстрого доступа)';
COMMENT ON COLUMN participants.joined_at IS 'Дата присоединения к чату';
COMMENT ON COLUMN participants.last_read_message_id IS 'ID последнего прочитанного сообщения';
COMMENT ON COLUMN participants.last_read_at IS 'Дата последнего прочтения';
COMMENT ON COLUMN participants.role IS 'Роль участника: OWNER, ADMIN, MEMBER';
COMMENT ON COLUMN participants.nickname IS 'Пользовательское имя в чате';
COMMENT ON COLUMN participants.muted_until IS 'Дата окончания мута (NULL - не в муте)';
COMMENT ON COLUMN participants.is_pinned IS 'Флаг закрепленного чата';
COMMENT ON COLUMN participants.notification_settings IS 'Настройки уведомлений в формате JSON';
