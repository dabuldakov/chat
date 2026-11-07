-- Долгоживущие токены для ботов/интеграций (например, моста OpenCode).
-- Храним только SHA-256 от токена; сам токен показывается один раз при выдаче.
CREATE TABLE service_tokens (
    id           BIGSERIAL PRIMARY KEY,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    user_id      BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name         VARCHAR(100),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL
);

CREATE INDEX idx_service_tokens_user_id ON service_tokens(user_id);
