package com.chat.server.integration;

import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void contactsEndpointReturnsContactWithOnlineField() throws Exception {
        User owner = users.createUser("owner1");
        User friend = users.createUser("friend1");

        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(friend.getUserUuid());
        contacts.addContact(owner.getUserId(), request);

        mvc.perform(get("/api/contacts").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].contactUserUuid").value(friend.getUserUuid().toString()))
                .andExpect(jsonPath("$[0].online").value(false));
    }

    @Test
    void contactLifecycleOverHttp() throws Exception {
        User owner = users.createUser("contact_owner");
        User friend = users.createUser("contact_friend");

        mvc.perform(post("/api/contacts")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactUserUuid\":\"" + friend.getUserUuid()
                                + "\",\"contactName\":\"Friend\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactUserUuid").value(friend.getUserUuid().toString()))
                .andExpect(jsonPath("$.contactName").value("Friend"));

        UUID contactUuid = contacts.getUserContacts(owner.getUserId()).get(0).getContactUuid();

        mvc.perform(get("/api/contacts/page").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/contacts/search").param("query", "Friend").with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/contacts/{contactUuid}", contactUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactUuid").value(contactUuid.toString()));

        mvc.perform(patch("/api/contacts/{contactUuid}/name", contactUuid)
                        .param("name", "Renamed")
                        .with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/contacts/{contactUuid}", contactUuid).with(auth(owner)))
                .andExpect(jsonPath("$.contactName").value("Renamed"));

        mvc.perform(delete("/api/contacts/{contactUuid}", contactUuid).with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/contacts").with(auth(owner)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}