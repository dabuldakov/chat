-- Идемпотентность отправки: клиент присылает свой UUID сообщения и при
-- повторной попытке (обрыв сети после записи на сервере) получает уже
-- созданное сообщение, а не дубль.
--
-- Отдельная непартиционированная таблица: messages партиционирована по
-- created_at, а уникальный индекс на партиционированной таблице обязан
-- включать ключ партиционирования. UUID клиента глобально уникален.
CREATE TABLE message_client_ids (
    client_message_id UUID PRIMARY KEY,
    chat_id           BIGINT NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE,
    message_id        BIGINT NOT NULL,
    sender_id         BIGINT NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_message_client_ids_message ON message_client_ids(message_id);
