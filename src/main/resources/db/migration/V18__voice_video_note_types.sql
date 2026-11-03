-- Новые типы сообщений: голосовые и видео-кружки.
-- Ограничение было зафиксировано в V11 и не пропускало VOICE/VIDEO_NOTE.
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_message_type_check
    CHECK (message_type IN (
        'TEXT', 'IMAGE', 'VIDEO', 'GIF', 'AUDIO', 'FILE',
        'VOICE', 'VIDEO_NOTE', 'LOCATION', 'CONTACT', 'SYSTEM', 'DELETE'
    ));
