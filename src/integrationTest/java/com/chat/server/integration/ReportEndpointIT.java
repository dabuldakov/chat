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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-жалобы на пользовательский контент: сообщение, пользователь и чат;
 * идемпотентность повтора и запрет жаловаться на себя/своё сообщение.
 */
@AutoConfigureMockMvc
class ReportEndpointIT extends AbstractIntegrationTest {

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

    private User reporter;
    private User target;
    private UUID chatUuid;
    private Long chatId;

    @BeforeEach
    void setUp() {
        reporter = users.createUser("rep_reporter");
        target = users.createUser("rep_target");
        chatUuid = chats.createPrivateChat(reporter.getUserId(), target.getUserUuid()).getChatUuid();
        chatId = chats.getChatIdByUuid(chatUuid);
    }

    private UUID sendAs(User sender, String text) {
        return messages.sendMessage(chatId, sender.getUserId(), text,
                Message.MessageType.TEXT, null, null).getMessageUuid();
    }

    private void report(String type, UUID contentUuid, String reason, int expected) throws Exception {
        mvc.perform(post("/api/reports")
                        .with(auth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"" + type + "\",\"contentUuid\":\""
                                + contentUuid + "\",\"reason\":\"" + reason
                                + "\",\"comment\":\"test\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    void reportsCanBeCreatedForMessageUserAndChat() throws Exception {
        UUID message = sendAs(target, "bad");

        mvc.perform(post("/api/reports")
                        .with(auth(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"MESSAGE\",\"contentUuid\":\"" + message
                                + "\",\"reason\":\"ABUSE\",\"comment\":\"rude\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("MESSAGE"))
                .andExpect(jsonPath("$.status").value("NEW"));

        report("USER", target.getUserUuid(), "SPAM", 201);
        report("CHAT", chatUuid, "OTHER", 201);
    }

    @Test
    void duplicateReportIsIdempotent() throws Exception {
        UUID message = sendAs(target, "bad");

        report("MESSAGE", message, "SPAM", 201);
        report("MESSAGE", message, "SPAM", 201);
    }

    @Test
    void ownMessageAndSelfReportAreRejected() throws Exception {
        UUID mine = sendAs(reporter, "mine");
        report("MESSAGE", mine, "SPAM", 400);
        report("USER", reporter.getUserUuid(), "SPAM", 400);
    }

    @Test
    void cannotReportMessageFromForeignChat() throws Exception {
        User stranger = users.createUser("rep_stranger");
        UUID foreignChat = chats.createPrivateChat(target.getUserId(), stranger.getUserUuid())
                .getChatUuid();
        Long foreignChatId = chats.getChatIdByUuid(foreignChat);
        UUID foreignMessage = messages.sendMessage(foreignChatId, target.getUserId(),
                "foreign", Message.MessageType.TEXT, null, null).getMessageUuid();

        report("MESSAGE", foreignMessage, "ABUSE", 403);
    }
}
