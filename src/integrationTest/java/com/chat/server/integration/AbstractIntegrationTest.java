package com.chat.server.integration;

import com.chat.server.integration.containers.TestContainersRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * База для всех интеграционных тестов:
 * - поднимает полный Spring-контекст приложения;
 * - источник данных указывает на PostgreSQL в контейнере (testcontainers);
 * - очищает таблицы между тестами для изоляции.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
public abstract class AbstractIntegrationTest {

    private static final List<String> CLEANUP_TABLES = List.of(
            "message_statuses", "messages", "attachments", "participants",
            "blocked_users", "contacts", "chats", "user_sessions", "users");

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected CacheManager cacheManager;

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersRegistry.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersRegistry.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestContainersRegistry.POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("minio.url", TestContainersRegistry::minioUrl);
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.avatar-bucket", () -> "avatars");
        registry.add("minio.attachment-bucket", () -> "attachments");
    }

    @BeforeEach
    @AfterEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String table : CLEANUP_TABLES) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}