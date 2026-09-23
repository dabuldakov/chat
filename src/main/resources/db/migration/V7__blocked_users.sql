-- =====================================================
-- ТАБЛИЦА blocked_users (заблокированные пользователи)
-- =====================================================

CREATE TABLE blocked_users (
    block_id         BIGSERIAL PRIMARY KEY,
    block_uuid       UUID      NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id          BIGINT    NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    blocked_user_id  BIGINT    NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    reason           TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT    DEFAULT 0,
    CONSTRAINT uk_user_blocked UNIQUE (user_id, blocked_user_id)
);

-- UNIQUE(user_id, blocked_user_id) покрывает idx по user_id.
CREATE INDEX idx_blocked_blocked_id ON blocked_users(blocked_user_id);
CREATE INDEX idx_blocked_created_at ON blocked_users(created_at DESC);

CREATE TRIGGER update_blocked_users_updated_at
    BEFORE UPDATE ON blocked_users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
