package com.chat.server.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Все доменные эндпоинты закрыты аутентификацией через HTTP. Тест фиксирует
 * это на уровне безопасности (рутинги + фильтры), а не через сервисы: раньше
 * часть контроллеров вообще не имела HTTP-покрытия.
 */
@AutoConfigureMockMvc
class EndpointSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/contacts",
            "/api/chats",
            "/api/users/me",
            "/api/users/search?query=alice",
            "/api/blocked",
            "/api/presence",
            "/api/sync",
            "/api/sync/status",
            "/api/attachments/chat/00000000-0000-0000-0000-000000000000",
            "/api/chats/00000000-0000-0000-0000-000000000000/participants"
    })
    void anonymousRequestsAreUnauthorized(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
