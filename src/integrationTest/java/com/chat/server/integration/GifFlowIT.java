package com.chat.server.integration;

import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.gif.GifCatalogRepository;
import com.chat.server.identity.JwtUtil;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной поток гифок: отправка GIF-сообщения (метаданные от клиента),
 * каталогизация, списки «Мои»/«Этот чат» и контроль доступа.
 */
@AutoConfigureMockMvc
class GifFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private GifCatalogRepository gifCatalogRepository;
    @Autowired
    private JwtUtil jwtUtil;

    private User author;
    private User member;
    private User outsider;
    private UUID chatUuid;

    private String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getUserUuid(), user.getUsername());
    }

    private String gifBody(String slug) {
        return "{\"messageType\":\"GIF\",\"gif\":{"
                + "\"provider\":\"klipy\",\"providerGifId\":\"" + slug + "\","
                + "\"title\":\"cat\",\"url\":\"https://cdn.klipy.com/" + slug + ".gif\","
                + "\"previewUrl\":\"https://cdn.klipy.com/" + slug + "-tiny.gif\","
                + "\"mp4Url\":\"https://cdn.klipy.com/" + slug + ".mp4\","
                + "\"width\":200,\"height\":150,\"rating\":\"g\"}}";
    }

    @BeforeEach
    void setUp() {
        author = createUser("gif-author");
        member = createUser("gif-member");
        outsider = createUser("gif-outsider");
        var chat = chatService.createPrivateChat(author.getUserId(), member.getUserUuid());
        chatUuid = chat.getChatUuid();
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    @Test
    void sendingGifStoresAttachmentAndCatalog() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gifBody("cat")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageType").value("GIF"))
                .andExpect(jsonPath("$.attachments[0].type").value("GIF"))
                .andExpect(jsonPath("$.attachments[0].fileUrl").value("https://cdn.klipy.com/cat.gif"))
                .andExpect(jsonPath("$.attachments[0].mp4Url").value("https://cdn.klipy.com/cat.mp4"))
                // Наш ключ гифки — разделяет сообщение и каталог/источник.
                .andExpect(jsonPath("$.attachments[0].gifKey").isNotEmpty());

        assertThat(gifCatalogRepository.findAll()).hasSize(1);
    }

    @Test
    void gifAppearsInMyRecentAndChatLists() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gifBody("cat")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/gifs/recent").header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerGifId").value("cat"));

        mvc.perform(get("/api/gifs/chat/{chatUuid}", chatUuid).header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerGifId").value("cat"));
    }

    @Test
    void sameGifIsDeduplicatedInCatalog() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gifBody("cat")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gifBody("cat")))
                .andExpect(status().isOk());

        // Одна и та же гифка (provider+slug) — одна запись каталога.
        assertThat(gifCatalogRepository.findAll()).hasSize(1);
    }

    @Test
    void outsiderCannotReadChatGifs() throws Exception {
        mvc.perform(get("/api/gifs/chat/{chatUuid}", chatUuid).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gifWithoutPayloadIsRejected() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"GIF\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankTextIsRejectedForTextMessages() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"TEXT\",\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
