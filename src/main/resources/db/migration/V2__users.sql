-- =====================================================
-- ТАБЛИЦА users (пользователи)
-- =====================================================

CREATE TABLE users (
    user_id                   BIGSERIAL PRIMARY KEY,
    user_uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    username                  VARCHAR(50)  NOT NULL UNIQUE,
    email                     VARCHAR(255) NOT NULL UNIQUE,
    password_hash             VARCHAR(255) NOT NULL,
    first_name                VARCHAR(100),
    last_name                 VARCHAR(100),
    avatar_url                VARCHAR(500),
    phone_number              VARCHAR(20),
    last_seen_at              TIMESTAMP,
    is_online                 BOOLEAN      DEFAULT FALSE,
    status                    VARCHAR(20)  DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'BANNED')),
    is_deleted                BOOLEAN      DEFAULT FALSE,
    deleted_at                TIMESTAMP,
    email_verified            BOOLEAN      DEFAULT FALSE,
    two_factor_enabled        BOOLEAN      DEFAULT FALSE,
    two_factor_secret         VARCHAR(255),
    reset_token               VARCHAR(255),
    reset_token_expiry        TIMESTAMP,
    email_verification_token  VARCHAR(255),
    created_at                TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    version                   BIGINT       DEFAULT 0
);

-- Уникальные индексы по username/email/user_uuid создаются самими
-- UNIQUE-ограничениями, дублирующие индексы не нужны.
CREATE INDEX idx_users_status                    ON users(status);
CREATE INDEX idx_users_reset_token               ON users(reset_token);
CREATE INDEX idx_users_email_verification_token  ON users(email_verification_token);
CREATE INDEX idx_users_created_at                ON users(created_at DESC);
CREATE INDEX idx_users_status_online             ON users(status, is_online);
CREATE INDEX idx_users_deleted_status            ON users(is_deleted, status);
CREATE INDEX idx_users_last_seen                 ON users(last_seen_at DESC) WHERE is_online = false;
CREATE INDEX idx_users_full_name                 ON users(first_name, last_name);
CREATE INDEX idx_users_username_lower            ON users(LOWER(username));
CREATE INDEX idx_users_reset_token_expiry        ON users(reset_token_expiry) WHERE reset_token IS NOT NULL;
CREATE INDEX idx_users_search_gin ON users
    USING GIN (to_tsvector('russian',
        COALESCE(username, '') || ' ' || COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')));

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Автоматическое обновление last_seen_at при выходе в онлайн.
CREATE OR REPLACE FUNCTION update_user_last_seen()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_online = true AND OLD.is_online = false THEN
        NEW.last_seen_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_last_seen
    BEFORE UPDATE OF is_online ON users
    FOR EACH ROW EXECUTE FUNCTION update_user_last_seen();
