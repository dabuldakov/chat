package com.chat.server.integration.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Включает кэширование для интеграционных тестов.
 * Сервисы используют аннотации @Cacheable/@CacheEvict ("users", "chats"),
 * без CacheManager их вызовы в тестах упадут.
 */
@Configuration
@EnableCaching
public class TestCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("users", "chats");
    }
}