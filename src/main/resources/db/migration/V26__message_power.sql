-- Сила «заряда» сообщения (0..100), клиент присылает при удержании кнопки.
ALTER TABLE messages
    ADD COLUMN power SMALLINT NOT NULL DEFAULT 0;
