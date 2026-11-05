package com.chat.server.integration;

import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-синхронизация: полная, только сообщения, только чаты, статус.
 */
@AutoConfigureMockMvc
class SyncEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;

    private User user;

    @BeforeEach
    void setUp() {
        user = users.createUser("sync_user");
    }

    @Test
    void syncEndpointsReturnUserData() throws Exception {
        mvc.perform(get("/api/sync").with(auth(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync")
                        .param("lastSyncTime", "2000-01-01T00:00:00Z")
                        .with(auth(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync/messages")
                        .param("lastSyncTime", "2000-01-01T00:00:00Z")
                        .with(auth(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync/chats").with(auth(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync/chats")
                        .param("lastSyncTime", "2000-01-01T00:00:00Z")
                        .with(auth(user)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync/status").with(auth(user)))
                .andExpect(status().isOk());
    }
}
