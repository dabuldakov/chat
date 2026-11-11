package com.chat.server.integration;

import com.chat.server.conversation.ChatService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-presence: heartbeat переводит в «в сети», batch-запрос отдаёт статус,
 * а неизвестные UUID не ломают ответ.
 */
@AutoConfigureMockMvc
class PresenceEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user = users.createUser("presence_user");
        other = users.createUser("presence_other");
    }

    @Test
    void heartbeatMarksOnlineAndBatchReturnsStatus() throws Exception {
        mvc.perform(post("/api/presence/heartbeat").with(auth(user)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/presence")
                        .param("userUuids", user.getUserUuid().toString(), other.getUserUuid().toString())
                        .with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userUuid").value(user.getUserUuid().toString()))
                .andExpect(jsonPath("$[0].online").value(true))
                .andExpect(jsonPath("$[1].online").value(false));

        mvc.perform(get("/api/presence")
                        .param("userUuids", UUID.randomUUID().toString())
                        .with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].online").value(false));
    }

    @Test
    void presenceIsLimitedToSharedChats() throws Exception {
        // other в сети, но общего чата нет: статус не раскрываем.
        mvc.perform(post("/api/presence/heartbeat").with(auth(other)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/presence")
                        .param("userUuids", other.getUserUuid().toString())
                        .with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].lastSeenAt").doesNotExist());

        // Появился общий чат — статус доступен.
        chats.createPrivateChat(user.getUserId(), other.getUserUuid());

        mvc.perform(get("/api/presence")
                        .param("userUuids", other.getUserUuid().toString())
                        .with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].online").value(true));
    }

    @Test
    void emptyBatchReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/presence").with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
