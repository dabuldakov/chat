package com.chat.server.integration;

import com.chat.server.conversation.ChatService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-сценарии чатов: создание приватного/группового, список, детали,
 * обновление, архив, квитанции, счётчики непрочитанного и удаление.
 */
@AutoConfigureMockMvc
class ChatEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private final ObjectMapper json = new ObjectMapper();

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = users.createUser("chat_owner");
        other = users.createUser("chat_other");
    }

    private UUID createGroup() throws Exception {
        String body = mvc.perform(post("/api/chats/group")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"team\",\"memberUuids\":[\""
                                + other.getUserUuid() + "\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("chatUuid").asText());
    }

    private UUID createPrivate() throws Exception {
        String body = mvc.perform(post("/api/chats/private")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherUserUuid\":\"" + other.getUserUuid() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatType").value("PRIVATE"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("chatUuid").asText());
    }

    @Test
    void privateAndGroupChatLifecycle() throws Exception {
        UUID privateChat = createPrivate();

        mvc.perform(get("/api/chats").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chatUuid").value(privateChat.toString()));
        mvc.perform(get("/api/chats/{chatUuid}", privateChat).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatUuid").value(privateChat.toString()))
                .andExpect(jsonPath("$.participantCount").value(2));

        UUID group = createGroup();
        mvc.perform(put("/api/chats/{chatUuid}", group)
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"renamed\",\"description\":\"desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("renamed"));

        mvc.perform(post("/api/chats/archive/{chatUuid}", group).with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chats/unarchive/{chatUuid}", group).with(auth(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chats/{chatUuid}/receipts", group).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/chats/unread-count/{chatUuid}", group).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/chats/unread-count/all").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mvc.perform(delete("/api/chats/{chatUuid}", group).with(auth(owner)))
                .andExpect(status().isOk());
        // После удаления чат недоступен (404, либо 403 из-за кэша id → участники удалены).
        mvc.perform(get("/api/chats/{chatUuid}", group).with(auth(owner)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void onlyCreatorCanUpdateAndPrivateChatCannotBeUpdated() throws Exception {
        UUID group = createGroup();
        mvc.perform(put("/api/chats/{chatUuid}", group)
                        .with(auth(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"nope\"}"))
                .andExpect(status().isForbidden());

        UUID privateChat = createPrivate();
        mvc.perform(put("/api/chats/{chatUuid}", privateChat)
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"nope\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void outsiderCannotReadChatDetailsOrReceipts() throws Exception {
        UUID group = createGroup();
        User outsider = users.createUser("chat_outsider");

        mvc.perform(get("/api/chats/{chatUuid}", group).with(auth(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/chats/{chatUuid}/receipts", group).with(auth(outsider)))
                .andExpect(status().isForbidden());
    }
}
