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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-сценарии управления участниками группы: список, добавление, роль,
 * mute/pin, «моё участие», выход и удаление.
 */
@AutoConfigureMockMvc
class ParticipantEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    private User owner;
    private User member;
    private User newbie;
    private UUID chatUuid;

    @BeforeEach
    void setUp() {
        owner = users.createUser("p_owner");
        member = users.createUser("p_member");
        newbie = users.createUser("p_newbie");
        chatUuid = chats.createGroupChat("team", owner.getUserId(), List.of(member.getUserUuid()))
                .getChatUuid();
    }

    @Test
    void participantsCanBeListedAddedPromotedMutedPinnedAndRemoved() throws Exception {
        mvc.perform(get("/api/chats/{chatUuid}/participants", chatUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(post("/api/chats/{chatUuid}/participants", chatUuid)
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberUuids\":[\"" + newbie.getUserUuid() + "\"]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chats/{chatUuid}/participants", chatUuid).with(auth(owner)))
                .andExpect(jsonPath("$.length()").value(3));

        mvc.perform(put("/api/chats/{chatUuid}/participants/{userUuid}/role",
                        chatUuid, member.getUserUuid())
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/chats/{chatUuid}/participants/mute", chatUuid)
                        .param("durationHours", "24")
                        .with(auth(member)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chats/{chatUuid}/participants/me", chatUuid).with(auth(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isMuted").value(true))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(delete("/api/chats/{chatUuid}/participants/mute", chatUuid).with(auth(member)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/chats/{chatUuid}/participants/pin", chatUuid).with(auth(member)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chats/{chatUuid}/participants/me", chatUuid).with(auth(member)))
                .andExpect(jsonPath("$.isPinned").value(true));
        mvc.perform(delete("/api/chats/{chatUuid}/participants/pin", chatUuid).with(auth(member)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/chats/{chatUuid}/participants/leave", chatUuid).with(auth(newbie)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/chats/{chatUuid}/participants/{userUuid}",
                        chatUuid, member.getUserUuid()).with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chats/{chatUuid}/participants", chatUuid).with(auth(owner)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyCreatorCanAddAndLeaveHasRules() throws Exception {
        mvc.perform(post("/api/chats/{chatUuid}/participants", chatUuid)
                        .with(auth(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberUuids\":[\"" + newbie.getUserUuid() + "\"]}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/chats/{chatUuid}/participants/leave", chatUuid).with(auth(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void contactCanBeAddedToGroup() throws Exception {
        mvc.perform(post("/api/contacts")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactUserUuid\":\"" + newbie.getUserUuid() + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/chats/{chatUuid}/participants", chatUuid)
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberUuids\":[\"" + newbie.getUserUuid() + "\"]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chats/{chatUuid}/participants", chatUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.userUuid == '%s')].username"
                        .formatted(newbie.getUserUuid())).value("p_newbie"));
    }
}
