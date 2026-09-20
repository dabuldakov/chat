-- =====================================================
-- Функция для автоматического обновления updated_at
-- Должна существовать раньше всех триггеров остальных
-- миграций, поэтому вынесена в отдельную миграцию.
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';