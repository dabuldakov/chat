package com.chat.server.presence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Настройки presence («в сети»).
 *
 * <p>Клиент шлёт heartbeat каждые {@link #heartbeatIntervalSeconds} секунд, пока
 * приложение на переднем плане. Сервер считает пользователя онлайн, если с
 * момента последнего heartbeat прошло меньше {@link #onlineTtlSeconds}.
 * Запас по TTL (втрое больше интервала) переживает пару потерянных запросов
 * из-за плохой сети и не «мигает» статусом.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.presence")
public class PresenceProperties {

    /** Как часто клиент шлёт heartbeat. */
    private long heartbeatIntervalSeconds = 15;

    /**
     * Окно «онлайна»: столько секунд последний heartbeat считается актуальным.
     * Должно быть заметно больше {@link #heartbeatIntervalSeconds}.
     */
    private long onlineTtlSeconds = 45;

    /** Максимум UUID за один запрос batch-presence (защита от слишком длинного URL). */
    private int maxBatchSize = 200;

    /**
     * Период PresenceSweeper в миллисекундах. Задан в мс, а не в секундах,
     * чтобы подставить в {@code @Scheduled(fixedDelayString = ...)} без
     * неоднозначного разбора единиц измерения.
     */
    private long sweepIntervalMs = 60_000;

    public Duration onlineTtl() {
        return Duration.ofSeconds(onlineTtlSeconds);
    }
}
