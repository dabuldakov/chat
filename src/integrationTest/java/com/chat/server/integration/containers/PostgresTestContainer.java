package com.chat.server.integration.containers;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Фабрика PostgreSQL контейнера для интеграционных тестов.
 * Версия образа совпадает с продакшен-конфигурацией (postgres:17).
 */
public final class PostgresTestContainer {

    public static final String IMAGE = "postgres:17-alpine";
    public static final String DATABASE = "chat_db_test";
    public static final String USERNAME = "chat_test";
    public static final String PASSWORD = "chat_test_pwd";

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName(DATABASE)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withCommand("postgres",
                        "-c", "fsync=off",
                        "-c", "synchronous_commit=off",
                        "-c", "full_page_writes=off");
    }
}