package com.chat.server.integration;

import com.chat.server.attachment.Attachment;
import com.chat.server.message.Message;
import com.chat.server.chat.Participant;
import com.chat.server.user.User;
import com.chat.server.exception.NotFoundException;
import com.chat.server.attachment.AttachmentRepository;
import com.chat.server.chat.ChatRepository;
import com.chat.server.message.MessageRepository;
import com.chat.server.chat.ParticipantRepository;
import com.chat.server.user.UserRepository;
import com.chat.server.attachment.AttachmentService;
import com.chat.server.chat.ChatService;
import com.chat.server.attachment.FileUploadService;
import com.chat.server.message.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private AttachmentService attachmentService;
    @Autowired
    private FileUploadService fileUploadService;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        user1 = createUser("user1");
        user2 = createUser("user2");
        user3 = createUser("user3");
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
    void shouldCreatePrivateChatWithTwoParticipants() {
        var response = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());

        assertThat(response.getChatUuid()).isNotNull();
        assertThat(response.getChatType()).isEqualTo("PRIVATE");
        assertThat(response.getParticipantCount()).isEqualTo(2L);

        Long chatId = chatRepository.findByChatUuid(response.getChatUuid()).get().getChatId();
        List<Participant> participants = participantRepository.findAllByChatId(chatId);

        assertThat(participants).hasSize(2);
        assertThat(participants)
                .extracting(Participant::getRole)
                .containsExactlyInAnyOrder(
                        Participant.ParticipantRole.OWNER,
                        Participant.ParticipantRole.MEMBER);
    }

    @Test
    void shouldReturnExistingPrivateChatOnDuplicateCreation() {
        var first = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());
        var second = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());

        assertThat(second.getChatUuid()).isEqualTo(first.getChatUuid());
        assertThat(participantRepository.findAllByUserId(user1.getUserId())).hasSize(1);
    }

    @Test
    void shouldCreateGroupChatWithCreatorAndMembers() {
        var response = chatService.createGroupChat(
                "Test group",
                user1.getUserId(),
                List.of(user2.getUserUuid(), user3.getUserUuid()));

        assertThat(response.getChatType()).isEqualTo("GROUP");
        assertThat(response.getParticipantCount()).isEqualTo(3L);

        Long chatId = chatRepository.findByChatUuid(response.getChatUuid()).get().getChatId();
        List<Participant> participants = participantRepository.findAllByChatId(chatId);

        assertThat(participants).hasSize(3);
        assertThat(participants)
                .filteredOn(p -> p.getRole() == Participant.ParticipantRole.OWNER)
                .singleElement()
                .extracting(Participant::getUserId)
                .isEqualTo(user1.getUserId());
    }

    @Test
    void shouldDeleteChatWithMessagesAndAttachments() {
        var response = chatService.createGroupChat(
                "Deletable group", user1.getUserId(), List.of(user2.getUserUuid()));
        Long chatId = chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow().getChatId();

        Message message = messageService.sendMessage(
                chatId, user1.getUserId(), "hi", Message.MessageType.TEXT, null, null);

        var file = new MockMultipartFile("file", "secret.txt", "text/plain",
                "data".getBytes(StandardCharsets.UTF_8));
        Attachment attachment = attachmentService.uploadAttachment(file, chatId, user1.getUserId());
        String fileUrl = attachment.getFileUrl();

        chatService.deleteChat(chatId, user1.getUserId());

        assertThat(chatRepository.findByChatUuid(response.getChatUuid())).isEmpty();
        assertThat(participantRepository.findAllByChatId(chatId)).isEmpty();
        assertThat(messageRepository.findAllMessageIdsByChatId(chatId)).isEmpty();
        assertThat(attachmentRepository.findByAttachmentUuid(attachment.getAttachmentUuid())).isEmpty();
        assertThatThrownBy(() -> fileUploadService.load(fileUrl)).isInstanceOf(NotFoundException.class);
    }
}