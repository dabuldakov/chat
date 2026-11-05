package com.chat.server.integration;

import com.chat.server.conversation.ChatService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import com.chat.server.notification.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end проверка ответов на сообщения: сервер хранит reply_to_message_id,
 * а клиенту обязан вернуть replyToMessageUuid и в эхе отправки, и в истории.
 */
@AutoConfigureMockMvc
class MessageReplyIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private final ObjectMapper json = new ObjectMapper();

    private User author;
    private User peer;
    private UUID chatUuid;

    @BeforeEach
    void setUp() {
        author = users.createUser("reply_author");
        peer = users.createUser("reply_peer");
        chatUuid = chats.createPrivateChat(author.getUserId(), peer.getUserUuid()).getChatUuid();
    }

    private UUID send(User sender, UUID chat, String text, UUID replyTo) throws Exception {
        String body = "{\"text\":\"" + text + "\",\"messageType\":\"TEXT\""
                + (replyTo == null ? "" : ",\"replyToMessageUuid\":\"" + replyTo + "\"") + "}";
        String response = mvc.perform(post("/api/messages/{chatUuid}", chat)
                        .with(auth(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("messageUuid").asText());
    }

    @Test
    void sendResponseAndMessageListExposeReplyReference() throws Exception {
        UUID original = send(author, chatUuid, "original", null);
        UUID reply = send(author, chatUuid, "reply", original);

        mvc.perform(get("/api/messages/by-uuid/{messageUuid}", reply).with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyToMessageUuid").value(original.toString()));

        // История отдаётся «сначала новые», поэтому ответ — первый элемент.
        mvc.perform(get("/api/messages/{chatUuid}", chatUuid).with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].replyToMessageUuid").value(original.toString()));
    }

    @Test
    void replyThroughHttpSendEndpointKeepsTheReference() throws Exception {
        UUID original = send(peer, chatUuid, "peer original", null);
        String body = "{\"text\":\"answer\",\"messageType\":\"TEXT\",\"replyToMessageUuid\":\""
                + original + "\"}";

        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .with(auth(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyToMessageUuid").value(original.toString()));
    }

    @Test
    void replyToMessageFromAnotherChatIsRejected() throws Exception {
        User third = users.createUser("reply_third");
        UUID otherChat = chats.createPrivateChat(author.getUserId(), third.getUserUuid()).getChatUuid();
        UUID foreign = send(author, otherChat, "foreign", null);

        String body = "{\"text\":\"bad\",\"messageType\":\"TEXT\",\"replyToMessageUuid\":\""
                + foreign + "\"}";
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .with(auth(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
