package com.chat.server.integration.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Единая точка старта контейнеров для всех интеграционных тестов.
 * Контейнеры поднимаются один раз на JVM и переиспользуются всеми тестами.
 */
public final class TestContainersRegistry {

    private static final Logger log = LoggerFactory.getLogger(TestContainersRegistry.class);

    public static final PostgreSQLContainer<?> POSTGRES = PostgresTestContainer.create();

    @SuppressWarnings("resource")
    public static final GenericContainer<?> MINIO =
            new GenericContainer<>("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z")
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static {
        log.info("Starting PostgreSQL test container...");
        POSTGRES.start();
        log.info("PostgreSQL test container started at {}", POSTGRES.getJdbcUrl());

        log.info("Starting MinIO test container...");
        MINIO.start();
        log.info("MinIO test container started at {}", minioUrl());
    }

    public static String minioUrl() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private TestContainersRegistry() {
    }
}