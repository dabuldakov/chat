-- =====================================================
-- Общие объекты схемы: расширения и функции-триггеры.
-- Должны существовать до таблиц, которые их используют.
-- =====================================================

-- gen_random_uuid() входит в ядро PostgreSQL 13+. Расширение оставлено
-- для совместимости со старыми версиями и идемпотентно.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Единая функция автообновления updated_at для всех таблиц.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Денормализация user_uuid в participants + защита от рассогласования:
-- если пользователь не найден, вставка падает с явной ошибкой,
-- а не с невнятным нарушением NOT NULL.
CREATE OR REPLACE FUNCTION set_participant_user_uuid()
RETURNS TRIGGER AS $$
BEGIN
    SELECT u.user_uuid INTO NEW.user_uuid
    FROM users u
    WHERE u.user_id = NEW.user_id;

    IF NEW.user_uuid IS NULL THEN
        RAISE EXCEPTION 'Cannot resolve user_uuid for user_id=%', NEW.user_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
