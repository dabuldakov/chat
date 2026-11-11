-- Накопленное повреждение сообщения (0..100): растёт от ответов с зарядом.
ALTER TABLE messages
    ADD COLUMN damage SMALLINT NOT NULL DEFAULT 0;
