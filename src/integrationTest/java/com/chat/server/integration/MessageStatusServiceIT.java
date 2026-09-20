package com.chat.server.integration;

import com.chat.server.dto.response.MessageStatusDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.entity.MessageStatus;
import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.exception.BadRequestException;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.MessageStatusRepository;
import com.chat.server.repository.ParticipantRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageStatusServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private MessageStatusRepository messageStatusRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private MessageStatusService messageStatusService;
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
                .messageText("hello")
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

    private void createStatuses() {
        messageStatusService.createStatusesForMessage(message.getMessageId(),
                List.of(user1.getUserId(), user2.getUserId()), user1.getUserId());
    }

    @Test
    void shouldCreateStatusesForMessage() {
        createStatuses();

        List<MessageStatus> statuses = messageStatusRepository.findByMessageId(message.getMessageId());

        assertThat(statuses).hasSize(2);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user1.getUserId()))
                .singleElement()
                .extracting(MessageStatus::getStatus)
                .isEqualTo(MessageStatus.DeliveryStatus.READ);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user2.getUserId()))
                .singleElement()
                .extracting(MessageStatus::getStatus)
                .isEqualTo(MessageStatus.DeliveryStatus.SENT);
    }

    @Test
    void shouldMarkMessageAsDelivered() {
        createStatuses();

        messageStatusService.markMessageAsDelivered(message.getMessageUuid(), user2.getUserId());

        MessageStatus status = messageStatusRepository.findByMessageIdAndUserId(
                message.getMessageId(), user2.getUserId()).orElseThrow();
        assertThat(status.getStatus()).isEqualTo(MessageStatus.DeliveryStatus.DELIVERED);
        assertThat(status.getDeliveredAt()).isNotNull();
    }

    @Test
    void shouldMarkMessageAsRead() {
        createStatuses();

        messageStatusService.markMessageAsRead(message.getMessageUuid(), user2.getUserId());

        MessageStatus status = messageStatusRepository.findByMessageIdAndUserId(
                message.getMessageId(), user2.getUserId()).orElseThrow();
        assertThat(status.getStatus()).isEqualTo(MessageStatus.DeliveryStatus.READ);
        assertThat(status.getReadAt()).isNotNull();
    }

    @Test
    void shouldMarkMessagesAsReadInChatAndUpdateLastRead() {
        createStatuses();
        Message second = messageRepository.save(Message.builder()
                .chatId(chat.getChatId())
                .senderId(user2.getUserId())
                .messageText("second")
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());
        messageStatusService.createStatusesForMessage(second.getMessageId(),
                List.of(user1.getUserId(), user2.getUserId()), user2.getUserId());

        messageStatusService.markMessagesAsRead(chat.getChatId(), user2.getUserId(), second.getMessageUuid());

        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(MessageStatus.DeliveryStatus.READ);
        assertThat(messageStatusService.getMessageStatusForUser(second.getMessageUuid(), user2.getUserId()))
                .isEqualTo(MessageStatus.DeliveryStatus.READ);

        Participant participant = participantRepository.findByChatIdAndUserId(chat.getChatId(), user2.getUserId())
                .orElseThrow();
        assertThat(participant.getLastReadMessageId()).isEqualTo(second.getMessageId());
    }

    @Test
    void shouldUpdateLastReadMessage() {
        assertThat(participantRepository.findByChatIdAndUserId(chat.getChatId(), user2.getUserId())
                .orElseThrow().getLastReadMessageId()).isNull();

        messageStatusService.updateLastReadMessage(chat.getChatId(), user2.getUserId(), message.getMessageId());

        assertThat(participantRepository.findByChatIdAndUserId(chat.getChatId(), user2.getUserId())
                .orElseThrow().getLastReadMessageId()).isEqualTo(message.getMessageId());
    }

    @Test
    void shouldGetMessageStatuses() {
        createStatuses();

        List<MessageStatusDto> dtos = messageStatusService.getMessageStatuses(
                message.getMessageUuid(), chat.getChatId(), user1.getUserId());

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(MessageStatusDto::getUserId)
                .containsExactlyInAnyOrder(user1.getUserId(), user2.getUserId());
    }

    @Test
    void shouldRejectStatusesForMessageFromAnotherChat() {
        createStatuses();
        var third = createUser("u3");
        var other = chatService.createPrivateChat(user1.getUserId(), third.getUserUuid());
        Long otherChatId = chatRepository.findByChatUuid(other.getChatUuid()).orElseThrow().getChatId();

        assertThatThrownBy(() -> messageStatusService.getMessageStatuses(
                message.getMessageUuid(), otherChatId, user1.getUserId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectMarkingReadWithMessageFromAnotherChat() {
        var third = createUser("u4");
        var other = chatService.createPrivateChat(user1.getUserId(), third.getUserUuid());
        Long otherChatId = chatRepository.findByChatUuid(other.getChatUuid()).orElseThrow().getChatId();

        assertThatThrownBy(() -> messageStatusService.markMessagesAsRead(
                otherChatId, user1.getUserId(), message.getMessageUuid()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldReturnSentAsDefaultForUserWithoutStatus() {
        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(MessageStatus.DeliveryStatus.SENT);
    }

    @Test
    void shouldCheckIsMessageReadByAll() {
        createStatuses();
        assertThat(messageStatusService.isMessageReadByAll(message.getMessageUuid(), chat.getChatId())).isFalse();

        messageStatusService.markMessageAsRead(message.getMessageUuid(), user2.getUserId());

        assertThat(messageStatusService.isMessageReadByAll(message.getMessageUuid(), chat.getChatId())).isTrue();
    }

    @Test
    void shouldCountUnreadMessagesForUser() {
        createStatuses();
        assertThat(messageStatusService.getUnreadCountForUser(chat.getChatId(), user2.getUserId())).isEqualTo(1);
        assertThat(messageStatusService.getUnreadCountForUser(chat.getChatId(), user1.getUserId())).isZero();
    }
}