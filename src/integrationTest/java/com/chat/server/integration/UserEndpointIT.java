package com.chat.server.integration;

import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-профиль пользователя: чтение/обновление, FCM-токен и точечные lookup-и.
 */
@AutoConfigureMockMvc
class UserEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;

    private User user;

    @BeforeEach
    void setUp() {
        user = users.createUser("user_endpoint");
    }

    @Test
    void profileUpdateAndLookups() throws Exception {
        mvc.perform(get("/api/users/me").with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user_endpoint"))
                .andExpect(jsonPath("$.userUuid").value(user.getUserUuid().toString()));

        mvc.perform(put("/api/users/me")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ivan\",\"lastName\":\"Petrov\","
                                + "\"phoneNumber\":\"+70000000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ivan"))
                .andExpect(jsonPath("$.lastName").value("Petrov"))
                .andExpect(jsonPath("$.phoneNumber").value("+70000000000"));

        mvc.perform(put("/api/users/me/fcm-token")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"fcm-1\",\"deviceId\":\"device-1\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/{userUuid}", user.getUserUuid()).with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value(user.getUserUuid().toString()));
        mvc.perform(get("/api/users/search").param("query", "user_endpoint").with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("user_endpoint"));
        mvc.perform(get("/api/users/by-username/{username}", "user_endpoint").with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user_endpoint"));
        mvc.perform(get("/api/users/{userUuid}/status", user.getUserUuid()).with(auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").exists());
    }

    @Test
    void profileUpdateValidatesFieldLengths() throws Exception {
        String longName = "x".repeat(101);
        mvc.perform(put("/api/users/me")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
