package com.chat.server.storage;

/**
 * Порт объектного хранилища аватаров. Реализация — MinIO
 * ({@link MinioAvatarStorage}); в интеграционных тестах подменяется in-memory
 * реализацией.
 */
public interface AvatarStorage {

    void put(String key, byte[] image);

    byte[] get(String key);

    void delete(String key);
}
