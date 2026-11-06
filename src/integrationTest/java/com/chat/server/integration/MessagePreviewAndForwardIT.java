package com.chat.server.integration;

import com.chat.server.conversation.Attachment;
import com.chat.server.conversation.AttachmentRepository;
import com.chat.server.conversation.AttachmentService;
import com.chat.server.conversation.Chat;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatResponseDto;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import com.chat.server.notification.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Превью чата после редактирования/удаления и пересылка вложений:
 * список чатов не должен показывать удалённый/старый текст, а пересланное
 * изображение — терять вложение.
 */
class MessagePreviewAndForwardIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private MessageService messageService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private AttachmentService attachmentService;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private User user1;
    private User user2;
    private User other;
    private Chat chat;
    private Chat otherChat;

    @BeforeEach
    void setUp() {
        user1 = createUser("preview_u1");
        user2 = createUser("preview_u2");
        other = createUser("preview_other");
        chat = privateChat(user1, user2);
        otherChat = privateChat(user1, other);
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private Chat privateChat(User a, User b) {
        var response = chatService.createPrivateChat(a.getUserId(), b.getUserUuid());
        return chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
    }

    private String preview(User viewer) {
        return previewOf(chat, viewer);
    }

    private String previewOf(Chat target, User viewer) {
        ChatResponseDto dto = chatService.getUserChatsWithDetails(viewer.getUserId()).stream()
                .filter(c -> c.getChatUuid().equals(target.getChatUuid()))
                .findFirst()
                .orElseThrow();
        return dto.getLastMessage() == null ? null : dto.getLastMessage().getText();
    }

    @Test
    void editingTheLastMessageUpdatesChatPreview() {
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "first", Message.MessageType.TEXT, null, null);
        Message last = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "second", Message.MessageType.TEXT, null, null);

        messageService.editMessage(last.getMessageUuid(), user1.getUserId(), "edited");

        assertThat(preview(user2)).isEqualTo("edited");
    }

    @Test
    void deletingTheLastMessageFallsBackToPreviousPreview() {
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "first", Message.MessageType.TEXT, null, null);
        Message last = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "second", Message.MessageType.TEXT, null, null);

        messageService.deleteMessage(last.getMessageUuid(), user1.getUserId(), false);

        assertThat(preview(user2)).isEqualTo("first");
    }

    @Test
    void deletingTheOnlyMessageClearsPreview() {
        Message only = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "only", Message.MessageType.TEXT, null, null);

        messageService.deleteMessage(only.getMessageUuid(), user1.getUserId(), false);

        assertThat(preview(user2)).isNull();
    }

    @Test
    void forwardingAnImageCopiesItsAttachment() {
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        Attachment uploaded = attachmentService.uploadAttachment(
                png, null, chat.getChatId(), user1.getUserId(), 10, 20, null);
        Message original = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "caption", Message.MessageType.IMAGE, null,
                List.of(uploaded.getAttachmentUuid()));

        Message forwarded = messageService.forwardMessage(
                original.getMessageUuid(), otherChat.getChatId(), user1.getUserId());

        assertThat(forwarded.getMessageType()).isEqualTo(Message.MessageType.IMAGE);
        List<Attachment> copies = attachmentRepository.findByMessageId(forwarded.getMessageId());
        assertThat(copies).hasSize(1);
        assertThat(copies.get(0).getFileUrl()).isEqualTo(uploaded.getFileUrl());
        assertThat(copies.get(0).getChatId()).isEqualTo(otherChat.getChatId());
        assertThat(previewOf(otherChat, user1)).isEqualTo("caption");
    }

    @Test
    void forwardingAnImageWithoutCaptionKeepsPreviewNotEmpty() {
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        Attachment uploaded = attachmentService.uploadAttachment(
                png, null, chat.getChatId(), user1.getUserId(), 10, 20, null);
        Message original = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                null, Message.MessageType.IMAGE, null,
                List.of(uploaded.getAttachmentUuid()));

        messageService.forwardMessage(original.getMessageUuid(), otherChat.getChatId(), user1.getUserId());

        assertThat(previewOf(otherChat, user1)).isNotBlank();
    }
}
