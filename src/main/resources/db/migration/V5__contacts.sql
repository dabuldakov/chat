-- =====================================================
-- ТАБЛИЦА contacts (контакты пользователей)
-- =====================================================

CREATE TABLE IF NOT EXISTS contacts (
                                        contact_id BIGSERIAL PRIMARY KEY,
                                        contact_uuid UUID UNIQUE DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    contact_user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    contact_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_user_contact UNIQUE (user_id, contact_user_id)
    );

-- =====================================================
-- ИНДЕКСЫ
-- =====================================================

-- Основные индексы
CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_user_id ON contacts(contact_user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_uuid ON contacts(contact_uuid);
CREATE INDEX IF NOT EXISTS idx_contacts_created_at ON contacts(created_at DESC);

-- Составные индексы для частых запросов
CREATE INDEX IF NOT EXISTS idx_contacts_user_contact ON contacts(user_id, contact_user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_user_name ON contacts(user_id, contact_name);

-- Индекс для поиска по имени контакта
CREATE INDEX IF NOT EXISTS idx_contacts_contact_name_gin ON contacts USING GIN (to_tsvector('russian', COALESCE(contact_name, '')));

-- =====================================================
-- ТРИГГЕРЫ
-- =====================================================

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_contacts_updated_at ON contacts;
CREATE TRIGGER update_contacts_updated_at
    BEFORE UPDATE ON contacts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦЕ И ПОЛЯМ
-- =====================================================

COMMENT ON TABLE contacts IS 'Таблица контактов пользователей';
COMMENT ON COLUMN contacts.contact_id IS 'Внутренний ID контакта';
COMMENT ON COLUMN contacts.contact_uuid IS 'Внешний UUID контакта для API';
COMMENT ON COLUMN contacts.user_id IS 'ID пользователя, владельца контакта';
COMMENT ON COLUMN contacts.contact_user_id IS 'ID пользователя в контакте';
COMMENT ON COLUMN contacts.contact_name IS 'Пользовательское имя для контакта (может отличаться от реального имени)';
COMMENT ON COLUMN contacts.created_at IS 'Дата добавления в контакты';