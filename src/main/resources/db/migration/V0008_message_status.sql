-- =====================================================
-- ТАБЛИЦА message_statuses (статусы сообщений) С ПАРТИЦИОНИРОВАНИЕМ
-- =====================================================

-- Создаем партиционированную таблицу по HASH(message_id)
CREATE TABLE IF NOT EXISTS message_statuses (
                                                status_id BIGSERIAL,
                                                status_uuid UUID DEFAULT gen_random_uuid(),
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    PRIMARY KEY (status_id, message_id),
    CONSTRAINT uk_message_status UNIQUE (message_id, user_id)
    ) PARTITION BY HASH (message_id);

-- =====================================================
-- СОЗДАНИЕ ПАРТИЦИЙ (16 партиций для равномерного распределения)
-- =====================================================

DO $$
DECLARE
i INTEGER;
BEGIN
FOR i IN 0..15 LOOP
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS message_statuses_partition_%s PARTITION OF message_statuses
            FOR VALUES WITH (MODULUS 16, REMAINDER %s)
        ', i, i);
END LOOP;
END $$;

-- =====================================================
-- ИНДЕКСЫ (создаются на уровне партиционированной таблицы)
-- =====================================================

-- Основные индексы
CREATE INDEX IF NOT EXISTS idx_msg_status_message_id ON message_statuses(message_id);
CREATE INDEX IF NOT EXISTS idx_msg_status_user_id ON message_statuses(user_id);
CREATE INDEX IF NOT EXISTS idx_msg_status_status ON message_statuses(status);
CREATE INDEX IF NOT EXISTS idx_msg_status_updated_at ON message_statuses(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_msg_status_status_uuid ON message_statuses(status_uuid);

-- Составные индексы для частых запросов
CREATE INDEX IF NOT EXISTS idx_msg_status_message_user ON message_statuses(message_id, user_id);
CREATE INDEX IF NOT EXISTS idx_msg_status_user_status ON message_statuses(user_id, status);
CREATE INDEX IF NOT EXISTS idx_msg_status_message_status ON message_statuses(message_id, status);
CREATE INDEX IF NOT EXISTS idx_msg_status_user_message ON message_statuses(user_id, message_id);

-- Частичный индекс для непрочитанных сообщений
CREATE INDEX IF NOT EXISTS idx_msg_status_unread ON message_statuses(user_id, created_at DESC)
    WHERE status != 'READ';

-- Частичный индекс для доставленных сообщений
CREATE INDEX IF NOT EXISTS idx_msg_status_delivered ON message_statuses(delivered_at)
    WHERE delivered_at IS NOT NULL;

-- Частичный индекс для прочитанных сообщений
CREATE INDEX IF NOT EXISTS idx_msg_status_read ON message_statuses(read_at)
    WHERE read_at IS NOT NULL;

-- =====================================================
-- ОГРАНИЧЕНИЯ (CHECK)
-- =====================================================

-- Проверка допустимых значений статуса
ALTER TABLE message_statuses
DROP CONSTRAINT IF EXISTS chk_message_status_status;
ALTER TABLE message_statuses
    ADD CONSTRAINT chk_message_status_status
        CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'FAILED'));

-- =====================================================
-- ТРИГГЕРЫ
-- =====================================================

-- Триггер для updated_at
DROP TRIGGER IF EXISTS update_message_statuses_updated_at ON message_statuses;
CREATE TRIGGER update_message_statuses_updated_at
    BEFORE UPDATE ON message_statuses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Триггер для автоматической установки временных меток
CREATE OR REPLACE FUNCTION update_status_timestamps()
RETURNS TRIGGER AS $$
BEGIN
    -- Если статус меняется на DELIVERED и delivered_at не установлен
    IF NEW.status = 'DELIVERED' AND OLD.status != 'DELIVERED' AND NEW.delivered_at IS NULL THEN
        NEW.delivered_at = NOW();
END IF;

    -- Если статус меняется на READ
    IF NEW.status = 'READ' AND OLD.status != 'READ' THEN
        NEW.read_at = NOW();
        -- Если delivered_at не установлен, устанавливаем тоже
        IF NEW.delivered_at IS NULL THEN
            NEW.delivered_at = NEW.read_at;
END IF;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_message_statuses_timestamps ON message_statuses;
CREATE TRIGGER trg_message_statuses_timestamps
    BEFORE UPDATE ON message_statuses
    FOR EACH ROW
    EXECUTE FUNCTION update_status_timestamps();

-- =====================================================
-- КОММЕНТАРИИ К ТАБЛИЦЕ И ПОЛЯМ
-- =====================================================

COMMENT ON TABLE message_statuses IS 'Статусы доставки и прочтения сообщений (партиционировано по HASH(message_id))';
COMMENT ON COLUMN message_statuses.status_id IS 'Внутренний ID статуса';
COMMENT ON COLUMN message_statuses.status_uuid IS 'Внешний UUID статуса для API';
COMMENT ON COLUMN message_statuses.message_id IS 'ID сообщения (используется для партиционирования)';
COMMENT ON COLUMN message_statuses.user_id IS 'ID пользователя-получателя';
COMMENT ON COLUMN message_statuses.status IS 'Статус: SENT, DELIVERED, READ, FAILED';
COMMENT ON COLUMN message_statuses.delivered_at IS 'Время доставки сообщения пользователю';
COMMENT ON COLUMN message_statuses.read_at IS 'Время прочтения сообщения пользователем';

-- =====================================================
-- ПРОВЕРКА ПАРТИЦИЙ
-- =====================================================

-- Проверка созданных партиций
SELECT
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE tablename LIKE 'message_statuses_partition_%'
ORDER BY tablename;

-- Проверка размера партиций (после заполнения данными)
/*
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS size,
    n_live_tup as row_count
FROM pg_tables t
LEFT JOIN pg_stat_user_tables s ON t.tablename = s.relname
WHERE t.tablename LIKE 'message_statuses_partition_%'
ORDER BY t.tablename;
*/