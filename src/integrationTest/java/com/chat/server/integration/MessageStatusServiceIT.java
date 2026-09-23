package com.chat.server.integration;

import com.chat.server.dto.response.DeliveryStatusDto;
import com.chat.server.dto.response.MessageStatusDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.exception.BadRequestException;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.ParticipantRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Статусы доставки/прочтения теперь выводятся из watermark-ов участника,
 * а не из таблицы message_statuses.
 */
class MessageStatusServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
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

    @Test
    void shouldDeriveStatusesFromWatermarks() {
        List<MessageStatusDto> statuses = messageStatusService.getMessageStatuses(
                message.getMessageUuid(), chat.getChatId(), user1.getUserId());

        assertThat(statuses).hasSize(2);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user1.getUserId()))
                .singleElement()
                .extracting(MessageStatusDto::getStatus)
                .isEqualTo(DeliveryStatusDto.READ);
        assertThat(statuses).filteredOn(s -> s.getUserId().equals(user2.getUserId()))
                .singleElement()
                .extracting(MessageStatusDto::getStatus)
                .isEqualTo(DeliveryStatusDto.SENT);
    }

    @Test
    void shouldMarkMessageAsDelivered() {
        messageStatusService.markMessageAsDelivered(message.getMessageUuid(), user2.getUserId());

        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(DeliveryStatusDto.DELIVERED);
    }

    @Test
    void shouldMarkMessageAsRead() {
        messageStatusService.markMessageAsRead(message.getMessageUuid(), user2.getUserId());

        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(DeliveryStatusDto.READ);
    }

    @Test
    void shouldMarkMessagesAsReadInChatAndUpdateLastRead() {
        Message second = messageRepository.save(Message.builder()
                .chatId(chat.getChatId())
                .senderId(user2.getUserId())
                .messageText("second")
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());

        messageStatusService.markMessagesAsRead(chat.getChatId(), user2.getUserId(), second.getMessageUuid());

        assertThat(messageStatusService.getMessageStatusForUser(message.getMessageUuid(), user2.getUserId()))
                .isEqualTo(DeliveryStatusDto.READ);
        assertThat(messageStatusService.getMessageStatusForUser(second.getMessageUuid(), user2.getUserId()))
                .isEqualTo(DeliveryStatusDto.READ);

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
    void shouldNotMoveReadWatermarkBackwards() {
        Message second = messageRepository.save(Message.builder()
                .chatId(chat.getChatId())
                .senderId(user1.getUserId())
                .messageText("second")
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());

        messageStatusService.updateLastReadMessage(chat.getChatId(), user2.getUserId(), second.getMessageId());
        messageStatusService.updateLastReadMessage(chat.getChatId(), user2.getUserId(), message.getMessageId());

        assertThat(participantRepository.findByChatIdAndUserId(chat.getChatId(), user2.getUserId())
                .orElseThrow().getLastReadMessageId()).isEqualTo(second.getMessageId());
    }

    @Test
    void shouldGetMessageStatuses() {
        List<MessageStatusDto> dtos = messageStatusService.getMessageStatuses(
                message.getMessageUuid(), chat.getChatId(), user1.getUserId());

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(MessageStatusDto::getUserId)
                .containsExactlyInAnyOrder(user1.getUserId(), user2.getUserId());
    }

    @Test
    void shouldRejectStatusesForMessageFromAnotherChat() {
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
                .isEqualTo(DeliveryStatusDto.SENT);
    }

    @Test
    void shouldCheckIsMessageReadByAll() {
        assertThat(messageStatusService.isMessageReadByAll(message.getMessageUuid(), chat.getChatId())).isFalse();

        messageStatusService.markMessageAsRead(message.getMessageUuid(), user2.getUserId());

        assertThat(messageStatusService.isMessageReadByAll(message.getMessageUuid(), chat.getChatId())).isTrue();
    }

    @Test
    void shouldCountUnreadMessagesForUser() {
        assertThat(messageStatusService.getUnreadCountForUser(chat.getChatId(), user2.getUserId())).isEqualTo(1);
        assertThat(messageStatusService.getUnreadCountForUser(chat.getChatId(), user1.getUserId())).isZero();
    }
}
