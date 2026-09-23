package com.chat.server.integration;

import com.chat.server.auth.JwtUtil;
import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.message.Message;
import com.chat.server.user.User;
import com.chat.server.message.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chat.server.attachment.MinioAvatarStorage;
import com.chat.server.chat.ChatService;
import com.chat.server.contacts.ContactService;
import com.chat.server.user.UserService;
@AutoConfigureMockMvc
class AvatarFlowIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ContactService contacts;
    @Autowired ChatService chats;
    @Autowired JwtUtil jwt;
    @Autowired MessageRepository messages;
    @Autowired MinioAvatarStorage storage;
    private final ObjectMapper json = new ObjectMapper();

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getUserUuid(), user.getUsername()); }

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("file", "avatar.png", "image/png", output.toByteArray());
    }

    private String upload(User user) throws Exception {
        var body = mvc.perform(multipart("/api/users/me/avatar").file(image()).header("Authorization", token(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("avatarUrl").asText();
    }

    @Test
    void uploadReplaceDeleteAndPropagateToContactsPrivateChatAndMessages() throws Exception {
        var owner = users.createUser("avatarowner", "avatarowner@example.com", "password123");
        var viewer = users.createUser("viewer", "viewer@example.com", "password123");
        // Populate both cache keys before mutations to catch stale UUID/ID entries.
        users.getUserByUuid(owner.getUserUuid());
        users.getUserById(owner.getUserId());
        var request = new AddContactRequestDto();
        request.setContactUserUuid(owner.getUserUuid());
        contacts.addContact(viewer.getUserId(), request);
        var chat = chats.createPrivateChat(viewer.getUserId(), owner.getUserUuid());
        messages.save(Message.builder().messageUuid(java.util.UUID.randomUUID()).chatId(chats.getChatIdByUuid(chat.getChatUuid()))
                .senderId(owner.getUserId()).messageText("hello").messageType(Message.MessageType.TEXT)
                .isDeleted(false).build());

        String url = upload(owner);
        var bytes = mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(storage.get(url.substring("/api/avatars/".length()))).isEqualTo(bytes);
        mvc.perform(get("/api/users/me").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(url));
        mvc.perform(get("/api/contacts").header("Authorization", token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].avatarUrl").value(url));
        mvc.perform(get("/api/chats").header("Authorization", token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].avatarUrl").value(url));
        mvc.perform(get("/api/messages/" + chat.getChatUuid()).header("Authorization", token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].senderAvatar").value(url));

        String replacement = upload(owner);
        assertThat(replacement).isNotEqualTo(url);
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get(replacement)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.get(url.substring("/api/avatars/".length())))
                .isInstanceOf(com.chat.server.exception.NotFoundException.class);

        // Another user's delete endpoint must not affect the owner.
        mvc.perform(delete("/api/users/me/avatar").header("Authorization", token(viewer))).andExpect(status().isOk());
        mvc.perform(get(replacement)).andExpect(status().isOk());
        mvc.perform(delete("/api/users/me/avatar").header("Authorization", token(owner))).andExpect(status().isOk());
        mvc.perform(get(replacement)).andExpect(status().isNotFound());
        mvc.perform(get("/api/contacts").header("Authorization", token(viewer)))
                .andExpect(jsonPath("$[0].avatarUrl").isEmpty());
    }

    @Test
    void rejectsAnonymousMutationAndInvalidImages() throws Exception {
        mvc.perform(multipart("/api/users/me/avatar").file(image())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/me/avatar")).andExpect(status().isUnauthorized());
        var owner = users.createUser("invalidavatar", "invalidavatar@example.com", "password123");
        mvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", token(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/users/me/avatar")
                        .file(new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .header("Authorization", token(owner)))
                .andExpect(status().isBadRequest());
        assertThat(users.getUserById(owner.getUserId()).getAvatarUrl()).isNull();
    }
}
