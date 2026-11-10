-- Клиентский буфер переполнился: часть ошибок не была записана.
ALTER TABLE error_events
    ADD COLUMN buffer_overflowed BOOLEAN NOT NULL DEFAULT FALSE;
