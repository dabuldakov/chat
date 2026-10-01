-- Presence («в сети»).
--
-- Источник истины — last_seen_at: клиент шлёт heartbeat, сервер обновляет
-- last_seen_at, а «онлайн» вычисляется как last_seen_at > now() - TTL
-- (см. PresenceService). Схема самовосстанавливающаяся: убитое приложение
-- не оставляет пользователя «вечно онлайн» — он выпадает из окна TTL.
--
-- is_online остаётся денормализованным флагом для индексированных выборок
-- (findOnlineUsers) и поддерживается heartbeat'ом и PresenceSweeper'ом.

-- Триггер из V2 писал last_seen_at в момент перехода false -> true, то есть
-- поле означало «последний вход», а не «последняя активность». Он также
-- конфликтовал бы с явной записью last_seen_at из heartbeat. Убираем:
-- за присутствие теперь отвечает код (PresenceService).
DROP TRIGGER IF EXISTS trg_users_last_seen ON users;
DROP FUNCTION IF EXISTS update_user_last_seen();

-- Частичный индекс для PresenceSweeper: ищем «зависшие онлайн», чтобы снять
-- флаг. Условие в индексе совпадает с условием в запросе.
CREATE INDEX IF NOT EXISTS idx_users_online_last_seen
    ON users (last_seen_at) WHERE is_online = true;

-- Данные, записанные до появления heartbeat: флаг online мог быть не снят
-- (пользователь не нажал «выход»). Сбрасываем флаг — дальше его выставит
-- первый же heartbeat. last_seen_at оставляем: по нему TTL-деривация
-- корректно покажет «оффлайн», и он же служит «был в сети».
UPDATE users SET is_online = false WHERE is_online = true;
