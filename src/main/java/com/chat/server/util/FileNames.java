package com.chat.server.util;

/**
 * Утилиты для имён файлов, попадающих в HTTP-заголовки. Имя приходит от
 * пользователя, поэтому его нельзя подставлять в Content-Disposition как есть:
 * кавычки ломают заголовок, а CR/LF — потенциальная header injection.
 */
public final class FileNames {

    private static final int MAX_LENGTH = 200;

    private FileNames() {
    }

    /** Убирает управляющие символы, кавычки и разделители пути. */
    public static String sanitize(String name) {
        if (name == null) {
            return "file";
        }
        String cleaned = name
                .replaceAll("[\\r\\n\\t\\u0000-\\u001f\\u007f]", "_")
                .replaceAll("[\"/\\\\]", "_")
                .trim();
        if (cleaned.isEmpty()) {
            return "file";
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
