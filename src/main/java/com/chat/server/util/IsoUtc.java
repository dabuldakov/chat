package com.chat.server.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Парсинг дат/времени, которые присылает клиент в path/query-параметрах
 * (например, {@code /api/messages/{chatUuid}/after/{timestamp}} или
 * {@code lastSyncTime}).
 *
 * Принимает «чистый» LocalDateTime ({@code 2026-09-22T12:00:00}), в том числе
 * с дробной частью, и UTC-строку с суффиксом {@code Z}
 * ({@code 2026-09-22T12:00:00Z}) — всё трактуется как время в UTC,
 * без учёта часового пояса клиента.
 */
public final class IsoUtc {

    private IsoUtc() {
    }

    public static LocalDateTime parse(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.endsWith("Z")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            // Смещения вида +05:00 не используем: берём первые 19 символов как UTC.
            return LocalDateTime.parse(trimmed.substring(0, 19));
        }
    }
}