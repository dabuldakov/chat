package com.chat.server.integration.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Единая точка старта контейнеров для всех интеграционных тестов.
 * Контейнер поднимается один раз на JVM и переиспользуется всеми тестами.
 */
public final class TestContainersRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestContainersRegistry.class);

    public static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    static {
        log.info("Starting PostgreSQL test container...");
        POSTGRES.start();
        log.info("PostgreSQL test container started at {}", POSTGRES.getJdbcUrl());
    }

    private TestContainersRegistry() {
    }
}