-- =====================================================
-- ТАБЛИЦА contacts (контакты пользователей)
-- =====================================================

CREATE TABLE contacts (
    contact_id       BIGSERIAL PRIMARY KEY,
    contact_uuid     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id          BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    contact_user_id  BIGINT       NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    contact_name     VARCHAR(255),
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT       DEFAULT 0,
    CONSTRAINT uk_user_contact UNIQUE (user_id, contact_user_id)
);

-- UNIQUE(user_id, contact_user_id) уже покрывает idx по user_id.
CREATE INDEX idx_contacts_contact_user_id ON contacts(contact_user_id);
CREATE INDEX idx_contacts_created_at      ON contacts(created_at DESC);
CREATE INDEX idx_contacts_user_name       ON contacts(user_id, contact_name);
CREATE INDEX idx_contacts_contact_name_gin ON contacts
    USING GIN (to_tsvector('russian', COALESCE(contact_name, '')));

CREATE TRIGGER update_contacts_updated_at
    BEFORE UPDATE ON contacts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
