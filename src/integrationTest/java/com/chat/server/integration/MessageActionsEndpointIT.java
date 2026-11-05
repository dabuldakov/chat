package com.chat.server.integration;

import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-действия над сообщениями: редактирование, удаление, пересылка, pin,
 * отметки прочтения/доставки, статусы, поиск и пагинация before/after.
 */
@AutoConfigureMockMvc
class MessageActionsEndpointIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;
    @Autowired
    private MessageService messages;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private final ObjectMapper json = new ObjectMapper();

    private User author;
    private User peer;
    private UUID chatUuid;
    private Long chatId;

    @BeforeEach
    void setUp() {
        author = users.createUser("act_author");
        peer = users.createUser("act_peer");
        chatUuid = chats.createPrivateChat(author.getUserId(), peer.getUserUuid()).getChatUuid();
        chatId = chats.getChatIdByUuid(chatUuid);
    }

    private Message send(String text) {
        return messages.sendMessage(chatId, author.getUserId(), text,
                Message.MessageType.TEXT, null, null);
    }

    @Test
    void editDeleteAndForward() throws Exception {
        Message message = send("hello");
        UUID uuid = message.getMessageUuid();

        mvc.perform(put("/api/messages/{messageUuid}", uuid)
                        .with(auth(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"edited\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("edited"))
                .andExpect(jsonPath("$.isEdited").value(true));

        mvc.perform(delete("/api/messages/{messageUuid}", uuid).with(auth(author)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/by-uuid/{messageUuid}", uuid).with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").value(true));

        Message source = send("forward me");
        UUID ageChat = chats.createPrivateChat(author.getUserId(),
                users.createUser("act_third").getUserUuid()).getChatUuid();
        String body = "{\"messageUuid\":\"" + source.getMessageUuid()
                + "\",\"targetChatUuid\":\"" + ageChat + "\"}";
        mvc.perform(post("/api/messages/forward")
                        .with(auth(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // chatUuid в MessageDto пока не заполняется (в Message только chatId).
                .andExpect(jsonPath("$.text").value("forward me"));
    }

    @Test
    void pinUnpinAndPinnedList() throws Exception {
        Message message = send("pin me");

        mvc.perform(get("/api/messages/{chatUuid}/pin/{messageUuid}", chatUuid, message.getMessageUuid())
                        .with(auth(author)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/{chatUuid}/pinned", chatUuid).with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].messageUuid").value(message.getMessageUuid().toString()));

        mvc.perform(delete("/api/messages/{chatUuid}/pin/{messageUuid}",
                        chatUuid, message.getMessageUuid()).with(auth(author)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/{chatUuid}/pinned", chatUuid).with(auth(author)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readDeliveredStatusesAndSearch() throws Exception {
        Message first = send("hello one");
        Message second = send("hello two");

        mvc.perform(post("/api/messages/{chatUuid}/delivered", chatUuid)
                        .param("upToMessageUuid", second.getMessageUuid().toString())
                        .with(auth(peer)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/messages/{chatUuid}/read", chatUuid)
                        .param("upToMessageUuid", second.getMessageUuid().toString())
                        .with(auth(peer)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/messages/{chatUuid}/status/{messageUuid}",
                        chatUuid, first.getMessageUuid()).with(auth(author)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/messages/search").param("query", "hello").with(auth(author)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/messages/{chatUuid}/search", chatUuid)
                        .param("query", "hello").with(auth(author)))
                .andExpect(status().isOk());
    }

    @Test
    void beforeAndAfterPagination() throws Exception {
        Message first = send("first");
        Message second = send("second");

        mvc.perform(get("/api/messages/{chatUuid}/before/{messageUuid}",
                        chatUuid, second.getMessageUuid()).with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageUuid").value(first.getMessageUuid().toString()));

        mvc.perform(get("/api/messages/{chatUuid}/after/{timestamp}",
                        chatUuid, "2000-01-01T00:00:00Z").with(auth(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
