-- Реакции на сообщения. message_id без FK: messages партиционирована по
-- created_at, уникального ключа по message_id в одиночку нет.
CREATE TABLE message_reactions (
    id         BIGSERIAL   PRIMARY KEY,
    message_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_reaction UNIQUE (message_id, user_id, emoji)
);

CREATE INDEX idx_message_reactions_message ON message_reactions (message_id);
