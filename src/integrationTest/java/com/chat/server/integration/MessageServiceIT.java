package com.chat.server.integration;

import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.entity.MessageStatus;
import com.chat.server.dto.response.ChatResponseDto;
import com.chat.server.entity.User;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.MessageStatusRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageService;
import com.chat.server.service.PushNotificationService;
import com.chat.server.service.MessageStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class MessageServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private MessageStatusRepository messageStatusRepository;
    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageStatusService messageStatusService;
    @Autowired
    private ChatService chatService;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private User user1;
    private User user2;
    private User outsider;
    private Chat chat;

    @BeforeEach
    void setUp() {
        user1 = createUser("u1");
        user2 = createUser("u2");
        outsider = createUser("outsider");

        var response = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());
        chat = chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
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
    void shouldExposeUnreadCountInChatListMatchingTotal() {
        messageService.sendMessage(chat.getChatId(), user2.getUserId(),
                "one", Message.MessageType.TEXT, null, null);
        Message second = messageService.sendMessage(chat.getChatId(), user2.getUserId(),
                "two", Message.MessageType.TEXT, null, null);

        assertThat(chatService.getUserChatsWithDetails(user1.getUserId()))
                .filteredOn(c -> c.getChatUuid().equals(chat.getChatUuid()))
                .singleElement()
                .extracting(ChatResponseDto::getUnreadCount)
                .isEqualTo(2L);
        assertThat(chatService.getTotalUnreadCount(user1.getUserId())).isEqualTo(2L);

        messageStatusService.markMessagesAsRead(chat.getChatId(), user1.getUserId(), second.getMessageUuid());

        assertThat(chatService.getUserChatsWithDetails(user1.getUserId()))
                .filteredOn(c -> c.getChatUuid().equals(chat.getChatUuid()))
                .singleElement()
                .extracting(ChatResponseDto::getUnreadCount)
                .isEqualTo(0L);
        assertThat(chatService.getTotalUnreadCount(user1.getUserId())).isZero();
    }

    @Test
    void shouldSendMessageAndCreateStatusesAndUpdateChat() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "Hello world", Message.MessageType.TEXT, null, null);

        assertThat(message.getMessageId()).isNotNull();
        assertThat(message.getSenderId()).isEqualTo(user1.getUserId());

        assertThat(messageService.getTotalMessagesCount(chat.getChatId(), user1.getUserId())).isEqualTo(1);

        Chat reloaded = chatRepository.findByChatUuid(chat.getChatUuid()).orElseThrow();
        assertThat(reloaded.getMessageCount()).isEqualTo(1);
        assertThat(reloaded.getLastMessageText()).isEqualTo("Hello world");
        assertThat(reloaded.getLastMessageSenderId()).isEqualTo(user1.getUserId());

        var statuses = messageStatusRepository.findByMessageId(message.getMessageId());
        assertThat(statuses).hasSize(2);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user1.getUserId()))
                .singleElement()
                .extracting(MessageStatus::getStatus)
                .isEqualTo(MessageStatus.DeliveryStatus.READ);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user2.getUserId()))
                .singleElement()
                .extracting(MessageStatus::getStatus)
                .isEqualTo(MessageStatus.DeliveryStatus.SENT);

        verify(pushNotificationService).sendMessageNotification(Mockito.eq(message), Mockito.anyList());
    }

    @Test
    void shouldReplyToMessage() {
        Message original = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "original", Message.MessageType.TEXT, null, null);

        Message reply = messageService.sendMessage(chat.getChatId(), user2.getUserId(),
                "reply", Message.MessageType.TEXT, original.getMessageUuid(), null);

        assertThat(reply.getReplyToMessageId()).isEqualTo(original.getMessageId());
    }

    @Test
    void shouldGetChatMessages() {
        messageService.sendMessage(chat.getChatId(), user1.getUserId(), "first", Message.MessageType.TEXT, null, null);
        messageService.sendMessage(chat.getChatId(), user2.getUserId(), "second", Message.MessageType.TEXT, null, null);

        var page = messageService.getChatMessages(chat.getChatId(), user1.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Message::getMessageText)
                .containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void shouldRejectSendingToForeignChat() {
        assertThatThrownBy(() -> messageService.sendMessage(chat.getChatId(), outsider.getUserId(),
                "hi", Message.MessageType.TEXT, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectReadingForeignChat() {
        assertThatThrownBy(() -> messageService.getChatMessages(chat.getChatId(), outsider.getUserId(),
                PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldEditOwnMessage() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "before", Message.MessageType.TEXT, null, null);

        Message edited = messageService.editMessage(message.getMessageUuid(), user1.getUserId(), "after");

        assertThat(edited.getMessageText()).isEqualTo("after");
        assertThat(edited.getIsEdited()).isTrue();
        assertThat(edited.getEditHistory()).isNotEmpty();
    }

    @Test
    void shouldRejectEditingOthersMessage() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "before", Message.MessageType.TEXT, null, null);

        assertThatThrownBy(() -> messageService.editMessage(message.getMessageUuid(), user2.getUserId(), "after"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("edit");
    }

    @Test
    void shouldSoftDeleteMessage() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "secret text", Message.MessageType.TEXT, null, null);

        messageService.deleteMessage(message.getMessageUuid(), user1.getUserId(), false);

        Message deleted = messageService.getMessageByUuid(message.getMessageUuid());
        assertThat(deleted.getIsDeleted()).isTrue();
        assertThat(deleted.getDeletedBy()).isEqualTo(user1.getUserId());
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getMessageText()).isEqualTo("[Message deleted]");
    }

    @Test
    void shouldHardDeleteMessage() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "temporary", Message.MessageType.TEXT, null, null);

        messageService.deleteMessage(message.getMessageUuid(), user1.getUserId(), true);

        assertThatThrownBy(() -> messageService.getMessageById(message.getMessageId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(messageStatusRepository.findByMessageId(message.getMessageId())).isEmpty();
    }

    @Test
    void shouldForwardMessage() {
        Message original = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "forward me", Message.MessageType.TEXT, null, null);

        Message forwarded = messageService.forwardMessage(original.getMessageUuid(), chat.getChatId(), user2.getUserId());

        assertThat(forwarded.getMessageText()).isEqualTo("forward me");
        assertThat(forwarded.getForwardedFromMessageId()).isEqualTo(original.getMessageId());
        assertThat(forwarded.getForwardedFromUserId()).isEqualTo(user1.getUserId());
    }

    @Test
    void shouldRejectForwardingMessageFromForeignChat() {
        Message original = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "secret", Message.MessageType.TEXT, null, null);

        // outsider не участник chat, но состоит в otherChat — цель доступна, источник нет.
        var otherResponse = chatService.createPrivateChat(outsider.getUserId(), user1.getUserUuid());
        Chat otherChat = chatRepository.findByChatUuid(otherResponse.getChatUuid()).orElseThrow();

        assertThatThrownBy(() -> messageService.forwardMessage(
                original.getMessageUuid(), otherChat.getChatId(), outsider.getUserId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectUnpinningMessageFromAnotherChat() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "pinned", Message.MessageType.TEXT, null, null);
        messageService.pinMessage(chat.getChatId(), message.getMessageUuid(), user1.getUserId());

        var otherResponse = chatService.createPrivateChat(user2.getUserId(), outsider.getUserUuid());
        Chat otherChat = chatRepository.findByChatUuid(otherResponse.getChatUuid()).orElseThrow();

        assertThatThrownBy(() -> messageService.unpinMessage(
                otherChat.getChatId(), message.getMessageUuid(), user2.getUserId()))
                .isInstanceOf(BadRequestException.class);

        assertThat(messageService.getPinnedMessages(chat.getChatId())).hasSize(1);
    }

    @Test
    void shouldPinAndUnpinMessage() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "important", Message.MessageType.TEXT, null, null);

        messageService.pinMessage(chat.getChatId(), message.getMessageUuid(), user1.getUserId());
        assertThat(messageService.getPinnedMessages(chat.getChatId()))
                .extracting(Message::getMessageId)
                .containsExactly(message.getMessageId());

        messageService.unpinMessage(chat.getChatId(), message.getMessageUuid(), user2.getUserId());
        assertThat(messageService.getPinnedMessages(chat.getChatId())).isEmpty();
    }

    @Test
    void shouldSearchMessages() {
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "unique keyword foo", Message.MessageType.TEXT, null, null);
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "unrelated", Message.MessageType.TEXT, null, null);

        var page = messageService.searchMessages(user1.getUserId(), "foo", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Message::getMessageText)
                .containsExactly("unique keyword foo");
    }

    @Test
    void shouldSearchMessagesInChat() {
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "needle in haystack", Message.MessageType.TEXT, null, null);
        messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "nothing here", Message.MessageType.TEXT, null, null);

        var result = messageService.searchMessagesInChat(chat.getChatId(), user1.getUserId(), "needle", 10);

        assertThat(result).extracting(Message::getMessageText).containsExactly("needle in haystack");
    }

    @Test
    void shouldGetMessagesAfterGivenTime() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "recent", Message.MessageType.TEXT, null, null);

        var result = messageService.getMessagesAfter(chat.getChatId(), user1.getUserId(),
                LocalDateTime.now().minusHours(1));

        assertThat(result).extracting(Message::getMessageId).containsExactly(message.getMessageId());
    }

    @Test
    void shouldGetMessagesBeforeGivenTime() {
        Message old = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "old", Message.MessageType.TEXT, null, null);
        shiftCreatedAt(old, -10);

        Message recent = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "recent", Message.MessageType.TEXT, null, null);

        var result = messageService.getMessagesBeforeMessage(chat.getChatId(), user1.getUserId(),
                recent.getMessageUuid(), 10);

        assertThat(result).extracting(Message::getMessageId).containsExactly(old.getMessageId());
    }

    @Test
    void shouldMarkMessageAsReadThroughStatusService() {
        Message message = messageService.sendMessage(chat.getChatId(), user1.getUserId(),
                "deliver me", Message.MessageType.TEXT, null, null);

        messageStatusService.markMessageAsDelivered(message.getMessageUuid(), user2.getUserId());
        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(MessageStatus.DeliveryStatus.DELIVERED);

        messageStatusService.markMessageAsRead(message.getMessageUuid(), user2.getUserId());
        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(MessageStatus.DeliveryStatus.READ);
    }

    @Test
    void shouldNotFindMissingMessage() {
        assertThatThrownBy(() -> messageService.getMessageByUuid(java.util.UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private void shiftCreatedAt(Message message, int minutes) {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE messages SET created_at = now() + interval '" + minutes + " minutes' " +
                    "WHERE message_id = " + message.getMessageId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}