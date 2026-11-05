package com.chat.server.integration;

import com.chat.server.newspush.NewsPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Внутренний push-эндпоинт: закрыт общим секретом, валидирует payload и
 * делегирует в {@link NewsPushService}.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "push.internal-token=test-secret")
class InternalPushEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NewsPushService newsPushService;

    private static final String BODY = "{\"authorUsername\":\"alice\",\"newsId\":1,"
            + "\"title\":\"t\",\"body\":\"b\"}";

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        mvc.perform(post("/api/internal/push/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/internal/push/news")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesPayloadAndDelegates() throws Exception {
        mvc.perform(post("/api/internal/push/news")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorUsername\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/internal/push/news")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        verify(newsPushService).sendNewsNotification("alice", 1L, "t", "b");
    }
}
