-- =====================================================
-- Отказ от собственного источника свободных гифок (Openverse) и зеркалирования.
-- Оставляем только Klipy: медиа не храним, каталог несёт лишь метаданные.
-- Убираем ненужные колонки каталога.
-- =====================================================

ALTER TABLE gif_catalog
    DROP COLUMN IF EXISTS object_key,
    DROP COLUMN IF EXISTS public_url,
    DROP COLUMN IF EXISTS mirrored,
    DROP COLUMN IF EXISTS license,
    DROP COLUMN IF EXISTS license_version,
    DROP COLUMN IF EXISTS creator,
    DROP COLUMN IF EXISTS attribution,
    DROP COLUMN IF EXISTS landing_url,
    DROP COLUMN IF EXISTS tags;
