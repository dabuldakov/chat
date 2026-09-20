-- =====================================================
-- Приведение JSON-полей к TEXT в соответствии с маппингом JPA.
--
-- Ранние версии миграций создавали эти колонки как jsonb, тогда как
-- соответствующие поля entity объявлены как String. На схеме, созданной
-- Hibernate (ddl-auto), колонки были строковыми, поэтому дрейф не проявлялся.
-- При схеме только из Flyway вставки падают с:
--   column "..." is of type jsonb but expression is of type character varying
--
-- Миграция идемпотентна:
--  * на свежих БД (V7/V8/V10 уже создают TEXT) ALTER TYPE text USING col::text
--    — no-op, DROP INDEX IF EXISTS — no-op;
--  * на ранее развёрнутых БД (колонки jsonb) — конвертирует jsonb -> text
--    и удаляет несовместимые GIN-индексы по jsonb.
-- =====================================================

drop index if exists idx_messages_mentioned_users;
drop index if exists idx_messages_metadata;
drop index if exists idx_participants_notification_settings;

alter table messages     alter column mentioned_users       type text using mentioned_users::text;
alter table messages     alter column metadata              type text using metadata::text;
alter table participants alter column notification_settings type text using notification_settings::text;
alter table attachments  alter column metadata              type text using metadata::text;
