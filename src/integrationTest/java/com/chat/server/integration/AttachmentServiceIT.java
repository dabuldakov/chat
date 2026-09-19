package com.chat.server.integration;

import com.chat.server.entity.Attachment;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.entity.User;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.AttachmentRepository;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.AttachmentService;
import com.chat.server.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentServiceIT extends AbstractIntegrationTest {

    private static final Path UPLOAD_DIR;

    static {
        try {
            UPLOAD_DIR = Files.createTempDirectory("chat-uploads");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> UPLOAD_DIR.toString());
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private ChatService chatService;

    private User user1;
    private User user2;
    private Chat chat;
    private Message message;

    @BeforeEach
    void setUp() {
        user1 = createUser("u1");
        user2 = createUser("u2");

        var response = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());
        chat = chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();

        message = messageRepository.save(Message.builder()
                .chatId(chat.getChatId())
                .senderId(user1.getUserId())
                .messageText("with attachment")
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());
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

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "photo.png", "image/png", "fake-image-content".getBytes());
    }

    private Attachment upload() {
        return attachmentService.uploadAttachment(imageFile(), chat.getChatId(), user1.getUserId());
    }

    @Test
    void shouldUploadAttachment() {
        Attachment attachment = upload();

        assertThat(attachment.getAttachmentId()).isNotNull();
        assertThat(attachment.getChatId()).isEqualTo(chat.getChatId());
        assertThat(attachment.getUploaderId()).isEqualTo(user1.getUserId());
        assertThat(attachment.getFileName()).isEqualTo("photo.png");
        assertThat(attachment.getFileUrl()).startsWith("attachments/");
        assertThat(attachment.getType()).isEqualTo(Attachment.AttachmentType.IMAGE);
        assertThat(attachment.getMimeType()).isEqualTo("image/png");
    }

    @Test
    void shouldRejectUploadToForeignChat() {
        User outsider = createUser("outsider");

        assertThatThrownBy(() -> attachmentService.uploadAttachment(imageFile(), chat.getChatId(), outsider.getUserId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldAttachToMessage() {
        Attachment attachment = upload();

        Attachment attached = attachmentService.attachToMessage(attachment.getAttachmentUuid(), message.getMessageUuid());

        assertThat(attached.getMessageId()).isEqualTo(message.getMessageId());

        Message reloaded = messageRepository.findById(message.getMessageId()).orElseThrow();
        assertThat(reloaded.getHasAttachments()).isTrue();
        assertThat(attachmentService.getAttachmentsByMessageId(message.getMessageId()))
                .extracting(Attachment::getAttachmentId)
                .containsExactly(attachment.getAttachmentId());
    }

    @Test
    void shouldRejectAttachFromDifferentChat() {
        Attachment attachment = upload();

        User outsider = createUser("outsider");
        var otherResponse = chatService.createPrivateChat(user1.getUserId(), outsider.getUserUuid());
        Chat otherChat = chatRepository.findByChatUuid(otherResponse.getChatUuid()).orElseThrow();
        Message otherMessage = messageRepository.save(Message.builder()
                .chatId(otherChat.getChatId())
                .senderId(user1.getUserId())
                .messageText("elsewhere")
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());

        assertThatThrownBy(() -> attachmentService.attachToMessage(attachment.getAttachmentUuid(), otherMessage.getMessageUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same chat");
    }

    @Test
    void shouldGetAttachmentByUuid() {
        Attachment attachment = upload();

        var found = attachmentService.getAttachmentByUuid(attachment.getAttachmentUuid());
        assertThat(found.getAttachmentId()).isEqualTo(attachment.getAttachmentId());

        assertThatThrownBy(() -> attachmentService.getAttachmentByUuid(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldGetAttachmentsByChatIdWithPagination() {
        upload();
        upload();

        List<Attachment> attachments = attachmentService.getAttachmentsByChatId(chat.getChatId(), 0, 10);

        assertThat(attachments).hasSize(2);
        assertThat(attachmentService.getAttachmentsCountByChatId(chat.getChatId())).isEqualTo(2);
        assertThat(attachmentService.getMediaCountByChatId(chat.getChatId())).isEqualTo(2);
    }

    @Test
    void shouldGetMediaAttachmentsByChatId() {
        upload();
        attachmentService.uploadAttachment(
                new MockMultipartFile("file", "doc.txt", "text/plain", "content".getBytes()),
                chat.getChatId(), user1.getUserId());

        List<Attachment> media = attachmentService.getMediaAttachmentsByChatId(chat.getChatId(), 0, 10);

        assertThat(media).hasSize(1);
        assertThat(media.get(0).getType()).isEqualTo(Attachment.AttachmentType.IMAGE);
    }

    @Test
    void shouldDeleteAttachmentByUploader() {
        Attachment attachment = upload();
        attachmentService.attachToMessage(attachment.getAttachmentUuid(), message.getMessageUuid());

        attachmentService.deleteAttachment(attachment.getAttachmentUuid(), user1.getUserId());

        assertThatThrownBy(() -> attachmentService.getAttachmentByUuid(attachment.getAttachmentUuid()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldRejectDeletingAttachmentNotOwnedByUser() {
        Attachment attachment = upload();
        attachmentService.attachToMessage(attachment.getAttachmentUuid(), message.getMessageUuid());

        assertThatThrownBy(() -> attachmentService.deleteAttachment(attachment.getAttachmentUuid(), user2.getUserId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot delete this attachment");
    }

    @Test
    void shouldReturnNotFoundForPreviewAndDownloadOfMissingFile() {
        Attachment attachment = upload();

        assertThatThrownBy(() -> attachmentService.getPreview(attachment))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> attachmentService.downloadAttachment(attachment))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldCleanupOrphanedAttachments() {
        Attachment attachment = upload();
        shiftCreatedAt(attachment, -48);

        attachmentService.cleanupOrphanedAttachments();

        assertThat(attachmentRepository.findById(attachment.getAttachmentId())).isEmpty();
    }

    @Test
    void shouldNotCleanupRecentlyUploadedOrphanedAttachment() {
        Attachment attachment = upload();

        attachmentService.cleanupOrphanedAttachments();

        assertThat(attachmentRepository.findById(attachment.getAttachmentId())).isPresent();
    }

    @Test
    void shouldDeleteAllAttachmentsByMessageId() {
        Attachment attachment = upload();
        attachmentService.attachToMessage(attachment.getAttachmentUuid(), message.getMessageUuid());

        attachmentService.deleteAllAttachmentsByMessageId(message.getMessageId());

        assertThat(attachmentRepository.findByMessageId(message.getMessageId())).isEmpty();
    }

    private void shiftCreatedAt(Attachment attachment, int hours) {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE attachments SET created_at = now() + interval '" + hours + " hours' " +
                    "WHERE attachment_id = " + attachment.getAttachmentId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}