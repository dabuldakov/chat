package com.chat.server.integration;

import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-сценарии блокировки: блокировка, список, пагинация, поиск, проверка
 * статуса, разблокировка одной записи и всех сразу.
 */
@AutoConfigureMockMvc
class BlockedUserEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;

    private final ObjectMapper json = new ObjectMapper();

    private User owner;
    private User target;

    @BeforeEach
    void setUp() {
        owner = users.createUser("block_owner");
        target = users.createUser("block_target");
    }

    private String block() throws Exception {
        String body = mvc.perform(post("/api/blocked")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockUserUuid\":\"" + target.getUserUuid()
                                + "\",\"reason\":\"spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedUserUuid").value(target.getUserUuid().toString()))
                .andExpect(jsonPath("$.reason").value("spam"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("blockUuid").asText();
    }

    @Test
    void blockListSearchCheckAndUnlockFlow() throws Exception {
        String blockUuid = block();

        mvc.perform(get("/api/blocked").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/blocked/page").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/blocked/search").param("query", "block").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/blocked/{blockUuid}", blockUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockUuid").value(blockUuid));

        mvc.perform(get("/api/blocked/check/{userUuid}", target.getUserUuid()).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedBy").value(false))
                // isMutualBlock в репозитории = «есть блокировка в любую сторону».
                .andExpect(jsonPath("$.mutual").value(true));
        mvc.perform(get("/api/blocked/check/{userUuid}", owner.getUserUuid()).with(auth(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.blockedBy").value(true));

        mvc.perform(delete("/api/blocked/{blockUuid}", blockUuid).with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/blocked").with(auth(owner)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unblockAllAndSelfBlockValidation() throws Exception {
        block();

        mvc.perform(delete("/api/blocked").with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/blocked").with(auth(owner)))
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/blocked")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockUserUuid\":\"" + owner.getUserUuid() + "\"}"))
                .andExpect(status().isConflict());
    }
}
