package com.chat.server.integration.support;

import com.chat.server.exception.NotFoundException;
import com.chat.server.storage.AvatarStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link AvatarStorage} для интеграционных тестов.
 * Ошибка при отсутствии объекта повторяет поведение MinIO
 * ({@link NotFoundException}).
 */
public class InMemoryAvatarStorage implements AvatarStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] image) {
        objects.put(key, image);
    }

    @Override
    public byte[] get(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new NotFoundException("Avatar not found");
        }
        return bytes;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
