-- =====================================================
-- Единый вход (auth-service/OIDC): выпиливание legacy-аутентификации chat.
--
-- 1. Пуш-токены переезжают из парольных сессий в отдельную таблицу device_tokens.
-- 2. Таблица user_sessions (парольные сессии) удаляется целиком.
-- 3. Из users убираются поля парольного входа и подтверждения email.
-- =====================================================

-- ---------- 1. Таблица устройств и FCM-токенов ----------
CREATE TABLE device_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_id   VARCHAR(255),
    fcm_token   VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    version     BIGINT       DEFAULT 0
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
-- Токен глобально уникален: приложение ищет запись по токену (Optional) и
-- «переезд» токена на другого пользователя оформляет удалением старой строки.
CREATE UNIQUE INDEX uk_device_tokens_fcm_token ON device_tokens(fcm_token);
-- Одно устройство — одна строка (для записей с известным device_id).
CREATE UNIQUE INDEX uk_device_tokens_user_device
    ON device_tokens(user_id, device_id) WHERE device_id IS NOT NULL;

-- ---------- 2. Переносим актуальные пуш-токены из сессий ----------
-- В user_sessions один и тот же токен мог дублироваться десятками сессий.
-- Оставляем только актуальные (is_active) и по строке на токен и на устройство:
-- при повторах берём самую свежую по last_activity. Строка, которая не является
-- самой свежей хотя бы по одному из двух ключей, отбрасывается — так перенос
-- гарантированно не нарушает ни один из уникальных индексов выше.
INSERT INTO device_tokens (user_id, device_id, fcm_token)
SELECT user_id, device_id, fcm_token
FROM (
    SELECT user_id, device_id, fcm_token,
           ROW_NUMBER() OVER (PARTITION BY fcm_token
                              ORDER BY last_activity DESC NULLS LAST) AS rn_token,
           ROW_NUMBER() OVER (PARTITION BY user_id, COALESCE(device_id, fcm_token)
                              ORDER BY last_activity DESC NULLS LAST) AS rn_device
    FROM user_sessions
    WHERE fcm_token IS NOT NULL
      AND is_active = TRUE
) latest
WHERE rn_token = 1
  AND rn_device = 1;

-- ---------- 3. Удаляем парольные сессии ----------
DROP TABLE user_sessions;

-- ---------- 4. Убираем legacy-поля парольного входа ----------
DROP INDEX IF EXISTS idx_users_reset_token;
DROP INDEX IF EXISTS idx_users_email_verification_token;
DROP INDEX IF EXISTS idx_users_reset_token_expiry;

ALTER TABLE users
    DROP COLUMN IF EXISTS password_hash,
    DROP COLUMN IF EXISTS reset_token,
    DROP COLUMN IF EXISTS reset_token_expiry,
    DROP COLUMN IF EXISTS email_verification_token,
    DROP COLUMN IF EXISTS two_factor_enabled,
    DROP COLUMN IF EXISTS two_factor_secret;
