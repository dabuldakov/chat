-- =====================================================
-- ТАБЛИЦА users (пользователи)
-- =====================================================

CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGSERIAL PRIMARY KEY,
                                     user_uuid UUID UNIQUE DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    avatar_url VARCHAR(500),
    phone_number VARCHAR(20),
    last_seen_at TIMESTAMP,
    is_online BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    email_verified BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_secret VARCHAR(255),
    reset_token VARCHAR(255),
    reset_token_expiry TIMESTAMP,
    email_verification_token VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

-- =====================================================
-- ОГРАНИЧЕНИЯ (CHECK)
-- =====================================================

-- Проверка допустимых значений статуса
ALTER TABLE users
DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE users
    ADD CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'BANNED'));

-- =====================================================
-- ИНДЕКСЫ
-- =====================================================

-- Основные индексы
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_user_uuid ON users(user_uuid);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_reset_token ON users(reset_token);
CREATE INDEX IF NOT EXISTS idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);

-- Составные индексы для частых запросов
CREATE INDEX IF NOT EXISTS idx_users_status_online ON users(status, is_online);
CREATE INDEX IF NOT EXISTS idx_users_deleted_status ON users(is_deleted, status);
CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(last_seen_at DESC) WHERE is_online = false;

-- Индекс для поиска по имени
CREATE INDEX IF NOT EXISTS idx_users_full_name ON users(first_name, last_name);
CREATE INDEX IF NOT EXISTS idx_users_username_lower ON users(LOWER(username));

-- Индекс для полнотекстового поиска
CREATE INDEX IF NOT EXISTS idx_users_search_gin ON users
    USING GIN (to_tsvector('russian', COALESCE(username, '') || ' ' || COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')));

-- Индекс для истекших токенов (для очистки)
CREATE INDEX IF NOT EXISTS idx_users_reset_token_expiry ON users(reset_token_expiry) WHERE reset_token IS NOT NULL;

-- =====================================================
-- ТРИГГЕРЫ
-- =====================================================

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦЕ И ПОЛЯМ
-- =====================================================

COMMENT ON TABLE users IS 'Таблица пользователей';
COMMENT ON COLUMN users.user_id IS 'Внутренний ID пользователя';
COMMENT ON COLUMN users.user_uuid IS 'Внешний UUID пользователя для API';
COMMENT ON COLUMN users.username IS 'Уникальное имя пользователя (логин)';
COMMENT ON COLUMN users.email IS 'Email пользователя';
COMMENT ON COLUMN users.password_hash IS 'Хеш пароля (BCrypt)';
COMMENT ON COLUMN users.first_name IS 'Имя';
COMMENT ON COLUMN users.last_name IS 'Фамилия';
COMMENT ON COLUMN users.avatar_url IS 'URL аватара пользователя';
COMMENT ON COLUMN users.phone_number IS 'Номер телефона';
COMMENT ON COLUMN users.last_seen_at IS 'Время последней активности';
COMMENT ON COLUMN users.is_online IS 'Флаг онлайн статуса';
COMMENT ON COLUMN users.status IS 'Статус аккаунта: ACTIVE, INACTIVE, SUSPENDED, BANNED';
COMMENT ON COLUMN users.is_deleted IS 'Флаг мягкого удаления';
COMMENT ON COLUMN users.deleted_at IS 'Время удаления аккаунта';
COMMENT ON COLUMN users.email_verified IS 'Флаг подтверждения email';
COMMENT ON COLUMN users.two_factor_enabled IS 'Флаг включения 2FA';
COMMENT ON COLUMN users.two_factor_secret IS 'Секрет для 2FA (TOTP)';
COMMENT ON COLUMN users.reset_token IS 'Токен для сброса пароля';
COMMENT ON COLUMN users.reset_token_expiry IS 'Время истечения токена сброса';
COMMENT ON COLUMN users.email_verification_token IS 'Токен для подтверждения email';

-- =====================================================
-- ФУНКЦИИ
-- =====================================================

-- Функция обновления last_seen_at
CREATE OR REPLACE FUNCTION update_user_last_seen()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_online = true AND OLD.is_online = false THEN
        NEW.last_seen_at = NOW();
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Триггер для автоматического обновления last_seen_at при изменении is_online
DROP TRIGGER IF EXISTS trg_users_last_seen ON users;
CREATE TRIGGER trg_users_last_seen
    BEFORE UPDATE OF is_online ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_user_last_seen();
