package com.chat.server.integration;

import com.chat.server.identity.ServiceTokenService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Service-токены ботов: выдача, проверка, отзыв и доступ к API. */
@AutoConfigureMockMvc
class ServiceTokenIT extends AbstractIntegrationTest {

    @Autowired
    private ServiceTokenService tokens;
    @Autowired
    private UserRepository users;
    @Autowired
    private MockMvc mvc;

    private User bot;

    @BeforeEach
    void setUp() {
        bot = users.save(User.builder()
                .username("opencode_bot")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    @Test
    void issuedTokenAuthenticatesAsItsUser() {
        String raw = tokens.issue(bot.getUserId(), "test");

        Optional<Long> userId = tokens.authenticate(raw);

        assertThat(userId).contains(bot.getUserId());
    }

    @Test
    void unknownAndMalformedTokensAreRejected() {
        assertThat(tokens.authenticate("mz_unknown")).isEmpty();
        assertThat(tokens.authenticate("not-a-token")).isEmpty();
        assertThat(tokens.authenticate(null)).isEmpty();
    }

    @Test
    void revokedTokenStopsWorking() {
        String raw = tokens.issue(bot.getUserId(), "test");

        tokens.revoke(raw);

        assertThat(tokens.authenticate(raw)).isEmpty();
    }

    @Test
    void serviceTokenGrantsApiAccess() throws Exception {
        String raw = tokens.issue(bot.getUserId(), "test");

        mvc.perform(get("/api/chats").header("Authorization", "Bearer " + raw))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chats").header("Authorization", "Bearer mz_invalid"))
                .andExpect(status().isUnauthorized());
    }
}
