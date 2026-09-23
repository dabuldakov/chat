-- =====================================================
-- ТАБЛИЦА participants (участники чатов)
-- user_uuid — денормализованное поле, заполняется триггером.
--
-- Статусы доставки/прочтения НЕ хранятся построчно (не таблица
-- message_statuses), а выводятся из watermark-ов участника:
--   last_delivered_message_id — докуда устройство подтвердило доставку;
--   last_read_message_id      — докуда пользователь прочитал.
-- Так вставка сообщения в группу на 1000 человек — это одна строка,
-- а не 1000 строк статусов.
-- =====================================================

CREATE TABLE participants (
    participant_id        BIGSERIAL PRIMARY KEY,
    chat_id               BIGINT      NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
    user_id               BIGINT      NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    user_uuid             UUID        NOT NULL,
    joined_at             TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    last_read_message_id  BIGINT,
    last_read_at          TIMESTAMP,
    last_delivered_message_id BIGINT,
    last_delivered_at     TIMESTAMP,
    role                  VARCHAR(20) DEFAULT 'MEMBER'
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    nickname              VARCHAR(100),
    muted_until           TIMESTAMP,
    is_pinned             BOOLEAN     DEFAULT FALSE,
    notification_settings TEXT        DEFAULT '{}',
    created_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT      DEFAULT 0,
    CONSTRAINT uk_participant_chat_user UNIQUE (chat_id, user_id)
);

-- (chat_id, user_id) и партиальный префикс chat_id покрыты UNIQUE-ограничением.
CREATE INDEX idx_participants_user_id    ON participants(user_id);
CREATE INDEX idx_participants_role       ON participants(role);
CREATE INDEX idx_participants_joined_at  ON participants(joined_at DESC);
CREATE INDEX idx_participants_user_uuid  ON participants(user_uuid);
CREATE INDEX idx_participants_user_chat  ON participants(user_id, chat_id);
CREATE INDEX idx_participants_chat_role  ON participants(chat_id, role);
CREATE INDEX idx_participants_user_role  ON participants(user_id, role);
CREATE INDEX idx_participants_last_read  ON participants(chat_id, last_read_message_id);
CREATE INDEX idx_participants_last_delivered ON participants(chat_id, last_delivered_message_id);
CREATE INDEX idx_participants_muted_until ON participants(muted_until) WHERE muted_until IS NOT NULL;
CREATE INDEX idx_participants_pinned     ON participants(user_id, is_pinned) WHERE is_pinned = true;

CREATE TRIGGER update_participants_updated_at
    BEFORE UPDATE ON participants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_participants_set_user_uuid
    BEFORE INSERT ON participants
    FOR EACH ROW EXECUTE FUNCTION set_participant_user_uuid();
