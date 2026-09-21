package com.chat.server.integration;

import com.chat.server.config.JwtUtil;
import com.chat.server.entity.User;
import com.chat.server.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регрессионные тесты безопасности {@link com.chat.server.controller.AuthController}.
 * <p>
 * Раньше {@code JwtAuthenticationFilter.isPublicEndpoint} помечал публичными все
 * {@code /api/auth/**} и пропускал JWT-фильтр. Из-за этого {@code Authentication} был
 * {@code null} и защищённые auth-эндпоинты ({@code /api/auth/me}, {@code /api/auth/logout*},
 * {@code /api/auth/change-password}) падали с HTTP 500. Тест фиксирует, что с валидным
 * токеном они работают, а без токена — отклоняются, при этом login/register остаются публичными.
 */
@AutoConfigureMockMvc
class AuthControllerSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private JwtUtil jwt;

    private String token;

    private void givenUser() {
        User user = users.createUser("authsec", "authsec@example.com", "password123");
        token = "Bearer " + jwt.generateToken(user.getUserUuid(), user.getUsername());
    }

    @Test
    void currentUserReturnsProfileForValidToken() throws Exception {
        givenUser();

        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authsec"));
    }

    @Test
    void currentUserRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWorksForValidToken() throws Exception {
        givenUser();

        mvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRejectsAnonymous() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordWorksForValidToken() throws Exception {
        givenUser();

        mvc.perform(post("/api/auth/change-password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"password123\",\"newPassword\":\"newPassword456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void registerStaysPublic() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"email\":\"newbie@example.com\","
                                + "\"password\":\"password123\",\"deviceType\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}