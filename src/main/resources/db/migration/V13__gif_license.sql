-- =====================================================
-- Лицензия/атрибуция и теги для гифок из свободных источников
-- (Openverse/Wikimedia: CC0, public domain, CC-BY). Нужны, чтобы легально
-- хранить зеркало и показывать атрибуцию там, где она обязательна.
-- =====================================================

ALTER TABLE gif_catalog ADD COLUMN license VARCHAR(64);
ALTER TABLE gif_catalog ADD COLUMN license_version VARCHAR(32);
ALTER TABLE gif_catalog ADD COLUMN creator VARCHAR(255);
ALTER TABLE gif_catalog ADD COLUMN attribution VARCHAR(1000);
ALTER TABLE gif_catalog ADD COLUMN landing_url VARCHAR(1000);
-- Теги: наши ключевые слова из названия источника + поисковый запрос.
ALTER TABLE gif_catalog ADD COLUMN tags VARCHAR(1000);
