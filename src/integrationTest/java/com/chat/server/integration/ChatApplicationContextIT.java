package com.chat.server.integration;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatApplicationContextIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void dataSourcePointsToTestPostgresContainer() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("chat_db_test");
        }
    }
}