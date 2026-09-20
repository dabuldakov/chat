-- =====================================================
-- Индексы для поиска и hot-путей сообщений.
--  * idx_messages_text_gin     — GIN по to_tsvector(message_text)
--                                для полнотекстового поиска (web_search/search).
--                                Создаётся на партиционированной таблице и
--                                каскадно применяется к каждой партиции.
--  * idx_attachments_chat_created — выдача вложений по чату (ORDER BY created_at).
-- =====================================================

create index if not exists idx_messages_text_gin
    on messages using gin (to_tsvector('russian', coalesce(message_text, '')));

create index if not exists idx_attachments_chat_created
    on attachments (chat_id, created_at desc);