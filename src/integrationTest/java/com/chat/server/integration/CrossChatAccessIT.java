package com.chat.server.integration;

import com.chat.server.conversation.Attachment;
import com.chat.server.conversation.AttachmentService;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозные (HTTP) проверки контроля доступа: посторонний пользователь не должен
 * ни читать, ни менять чужой чат — сообщения, вложения, участников.
 *
 * <p>Дополняет сервисные тесты ({@code MessageServiceIT}, {@code ParticipantServiceIT}):
 * ловит ситуации, когда контроллер забыл передать аутентифицированного пользователя
 * или вызвать проверку доступа, а также проверяет, что security-фильтр не пускает
 * запрос без токена.
 */
@AutoConfigureMockMvc
class CrossChatAccessIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private AttachmentService attachmentService;

    private User member;
    private User member2;
    private User intruder;
    private UUID chatUuid;
    private Message message;
    private UUID attachmentUuid;

    @BeforeEach
    void setUp() {
        member = createUser("member");
        member2 = createUser("member2");
        intruder = createUser("intruder");

        // Групповой чат: так проверки типа чата не маскируют проверку доступа
        // (для приватного часть эндпоинтов отвечает 409 раньше 403).
        var chat = chatService.createGroupChat("secret group", member.getUserId(),
                java.util.List.of(member2.getUserUuid()));
        chatUuid = chat.getChatUuid();
        Long chatId = chatRepository.findByChatUuid(chatUuid).orElseThrow().getChatId();

        message = messageService.sendMessage(chatId, member.getUserId(), "секрет",
                Message.MessageType.TEXT, null, null);

        var file = new MockMultipartFile("file", "secret.txt", "text/plain", "top secret".getBytes());
        Attachment attachment = attachmentService.uploadAttachment(file, null, chatId, member.getUserId(), null, null, null);
        attachmentUuid = attachment.getAttachmentUuid();
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    // ==================== Сообщения ====================

    @Test
    void intruderCannotReadMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadMessagesBefore() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/before/{messageUuid}", chatUuid, message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadNewMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/after/{timestamp}", chatUuid, "2020-01-01T00:00:00Z")
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadMessageByUuid() throws Exception {
        mvc.perform(get("/api/messages/by-uuid/{messageUuid}", message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotSearchInChat() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/search", chatUuid)
                        .param("query", "секрет")
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadPinnedMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/pinned", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadMessageStatuses() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/status/{messageUuid}", chatUuid, message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotSendMessage() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}", chatUuid)
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"вторжение\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotEditForeignMessage() throws Exception {
        mvc.perform(put("/api/messages/{messageUuid}", message.getMessageUuid())
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"изменено\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotDeleteForeignMessage() throws Exception {
        mvc.perform(delete("/api/messages/{messageUuid}", message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotMarkMessagesAsRead() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}/read", chatUuid)
                        .param("upToMessageUuid", message.getMessageUuid().toString())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotPinMessage() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/pin/{messageUuid}", chatUuid, message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    // ==================== Чат ====================

    @Test
    void intruderCannotReadChatDetails() throws Exception {
        mvc.perform(get("/api/chats/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadParticipants() throws Exception {
        mvc.perform(get("/api/chats/{chatUuid}/participants", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadUnreadCount() throws Exception {
        mvc.perform(get("/api/chats/unread-count/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotUpdateChat() throws Exception {
        mvc.perform(put("/api/chats/{chatUuid}", chatUuid)
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotAddParticipants() throws Exception {
        mvc.perform(post("/api/chats/{chatUuid}/participants", chatUuid)
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberUuids\":[\"" + member.getUserUuid() + "\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotRemoveParticipant() throws Exception {
        mvc.perform(delete("/api/chats/{chatUuid}/participants/{userUuid}", chatUuid, member.getUserUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotChangeParticipantRole() throws Exception {
        mvc.perform(put("/api/chats/{chatUuid}/participants/{userUuid}/role", chatUuid, member2.getUserUuid())
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotDeleteChat() throws Exception {
        mvc.perform(delete("/api/chats/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    // ==================== Вложения ====================

    @Test
    void intruderCannotDownloadAttachment() throws Exception {
        mvc.perform(get("/api/attachments/{attachmentUuid}", attachmentUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotPreviewAttachment() throws Exception {
        mvc.perform(get("/api/attachments/{attachmentUuid}/preview", attachmentUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotListChatAttachments() throws Exception {
        mvc.perform(get("/api/attachments/chat/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotListMessageAttachments() throws Exception {
        mvc.perform(get("/api/attachments/message/{messageUuid}", message.getMessageUuid())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    // ==================== Без токена ====================

    @Test
    void anonymousCannotReadMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}", chatUuid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadChatDetails() throws Exception {
        mvc.perform(get("/api/chats/{chatUuid}", chatUuid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void intruderCannotReadUpdatedMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/updated/{timestamp}", chatUuid, "2020-01-01T00:00:00Z")
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotToggleReaction() throws Exception {
        mvc.perform(post("/api/messages/{messageUuid}/reactions", message.getMessageUuid())
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"🔥\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotForwardForeignMessage() throws Exception {
        mvc.perform(post("/api/messages/forward")
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageUuid\":\"" + message.getMessageUuid()
                                + "\",\"targetChatUuid\":\"" + chatUuid + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotMarkDelivered() throws Exception {
        mvc.perform(post("/api/messages/{chatUuid}/delivered", chatUuid)
                        .param("upToMessageUuid", message.getMessageUuid().toString())
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReadReceipts() throws Exception {
        mvc.perform(get("/api/chats/{chatUuid}/receipts", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotArchiveChat() throws Exception {
        mvc.perform(post("/api/chats/archive/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotUnarchiveChat() throws Exception {
        mvc.perform(post("/api/chats/unarchive/{chatUuid}", chatUuid)
                        .with(auth(intruder)))
                .andExpect(status().isForbidden());
    }

    @Test
    void intruderCannotReportForeignMessage() throws Exception {
        mvc.perform(post("/api/reports")
                        .with(auth(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"MESSAGE\",\"contentUuid\":\""
                                + message.getMessageUuid() + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadUpdatedMessages() throws Exception {
        mvc.perform(get("/api/messages/{chatUuid}/updated/{timestamp}", chatUuid, "2020-01-01T00:00:00Z"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReadOwnProfile() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotSendErrorTelemetry() throws Exception {
        mvc.perform(post("/api/telemetry/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[],\"overflowed\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotReportContent() throws Exception {
        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"MESSAGE\",\"contentUuid\":\""
                                + message.getMessageUuid() + "\",\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }
}
