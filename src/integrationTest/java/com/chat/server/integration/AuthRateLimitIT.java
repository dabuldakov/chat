package com.chat.server.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Лимит частоты на логин: после серии неудачных попыток дальнейшие отклоняются
 * с 429, а не бесконечно перебирают пароль (по умолчанию 10 попыток за 5 минут).
 */
@AutoConfigureMockMvc
class AuthRateLimitIT extends AbstractIntegrationTest {

    private static final int MAX_ATTEMPTS = 10;

    @Autowired
    private MockMvc mvc;

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginIsRateLimitedAfterTooManyFailedAttempts() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("nobody", "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody", "wrong")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void differentAccountsAreLimitedIndependently() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("account-a", "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        // Другой аккаунт лимитом не задет.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("account-b", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
