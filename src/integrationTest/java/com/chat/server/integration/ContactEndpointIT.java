package com.chat.server.integration;

import com.chat.server.auth.JwtUtil;
import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactService;
import com.chat.server.user.User;
import com.chat.server.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что {@code GET /api/contacts} действительно отдаёт контакты через
 * HTTP вместе с полем {@code online}. Служебные тесты дергают сервис напрямую и
 * не поймали бы регрессию на уровне сериализации или безопасности.
 */
@AutoConfigureMockMvc
class ContactEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ContactService contacts;
    @Autowired
    private JwtUtil jwt;

    @Test
    void contactsEndpointReturnsContactWithOnlineField() throws Exception {
        User owner = users.createUser("owner1", "owner1@example.com", "password123");
        User friend = users.createUser("friend1", "friend1@example.com", "password123");

        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(friend.getUserUuid());
        contacts.addContact(owner.getUserId(), request);

        String token = "Bearer " + jwt.generateToken(owner.getUserUuid(), owner.getUsername());

        mvc.perform(get("/api/contacts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contactUserUuid").value(friend.getUserUuid().toString()))
                .andExpect(jsonPath("$[0].online").value(false));
    }
}