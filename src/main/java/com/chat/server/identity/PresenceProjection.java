package com.chat.server.identity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Проекция для чтения присутствия.
 *
 * <p>Отдельная проекция, а не сущность {@code User}: чтение идёт мимо
 * 10-минутного кэша пользователей. Иначе статус в списке контактов мог бы
 * отставать на TTL кэша — ровно тот баг, из-за которого индикатор «в сети»
 * показывал неверное значение.
 */
public interface PresenceProjection {

    Long getUserId();

    UUID getUserUuid();

    LocalDateTime getLastSeenAt();
}
