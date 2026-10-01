package com.chat.server.presence;

import java.time.LocalDateTime;

/**
 * Статус присутствия, отдаваемый клиенту.
 *
 * <p>{@code online} вычисляется из {@code lastSeenAt} и TTL, а не читается из
 * флага {@code is_online} в БД: последний может «залипнуть», если клиент не
 * успел сообщить об уходе. Время в БД хранится в UTC (см. {@code BaseEntity}).
 */
public record PresenceInfo(boolean online, LocalDateTime lastSeenAt) {

    /** Пользователь, о котором сервер ничего не знает (удалён, не найден). */
    public static PresenceInfo unknown() {
        return new PresenceInfo(false, null);
    }
}
