package com.chat.server.presence;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Статус присутствия одного пользователя в batch-ответе.
 *
 * <p>record, а не Lombok-класс: имя компонента попадает в JSON как есть
 * ({@code online}), без правила «срезать префикс is у boolean-геттера».
 * На Lombok-DTO с полем {@code boolean isOnline} Jackson отдаёт ключ
 * {@code online}, и клиент, ожидавший {@code isOnline}, молча получал
 * {@code false} — индикатор «в сети» не работал никогда.
 */
@Schema(description = "Статус присутствия пользователя")
public record PresenceResponse(

        @Schema(description = "UUID пользователя")
        UUID userUuid,

        @Schema(description = "Онлайн ли пользователь (heartbeat был недавно)")
        boolean online,

        @Schema(description = "Время последней активности (UTC)")
        LocalDateTime lastSeenAt
) {

    public static PresenceResponse of(UUID userUuid, PresenceInfo presence) {
        return new PresenceResponse(userUuid, presence.online(), presence.lastSeenAt());
    }
}
