package com.chat.server.integration.support;

import com.chat.server.storage.AvatarStorage;
import com.chat.server.storage.FileStorage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Подменяет MinIO-реализации хранилищ in-memory вариантами. Реальные бины
 * создаются, но не используются (MinioClient не подключается до первого вызова),
 * а MinIO-контейнер в тестах не нужен.
 */
@TestConfiguration
public class TestStorageConfig {

    @Bean
    @Primary
    public FileStorage inMemoryFileStorage() {
        return new InMemoryFileStorage();
    }

    @Bean
    @Primary
    public AvatarStorage inMemoryAvatarStorage() {
        return new InMemoryAvatarStorage();
    }
}
