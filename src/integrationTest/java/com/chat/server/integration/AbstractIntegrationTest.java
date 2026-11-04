package com.chat.server.integration;

import com.chat.server.integration.containers.TestContainersRegistry;
import com.chat.server.integration.support.TestStorageConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
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
@Import(TestStorageConfig.class)
public abstract class AbstractIntegrationTest {

    private static final List<String> CLEANUP_TABLES = List.of(
            "gif_user_usage", "gif_chat_usage", "gif_catalog",
            "messages", "attachments", "participants",
            "blocked_users", "contacts", "chats", "device_tokens", "users");

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected CacheManager cacheManager;

    /**
     * Аутентификация в MockMvc под единый IdP (auth-service): вместо legacy
     * HS256-токена chat кладём в SecurityContext principal, у которого
     * {@code getName()} — числовой {@code users.user_id}. Именно это читают
     * контроллеры ({@code Long.parseLong(authentication.getName())}).
     */
    protected static org.springframework.test.web.servlet.request.RequestPostProcessor auth(
            com.chat.server.identity.User user) {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user.getUserId().toString(), null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .authentication(authentication);
    }

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestContainersRegistry.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestContainersRegistry.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestContainersRegistry.POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
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