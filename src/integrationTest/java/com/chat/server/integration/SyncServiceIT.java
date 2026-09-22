package com.chat.server.integration;

import com.chat.server.entity.Chat;
import com.chat.server.entity.Message;
import com.chat.server.entity.User;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.MessageRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import com.chat.server.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SyncServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private SyncService syncService;

    private User user1;
    private User user2;
    private Chat chat;

    @BeforeEach
    void setUp() {
        user1 = createUser("u1");
        user2 = createUser("u2");

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

    private Message createMessage(Long senderId, String text) {
        return messageRepository.save(Message.builder()
                .chatId(chat.getChatId())
                .senderId(senderId)
                .messageText(text)
                .messageType(Message.MessageType.TEXT)
                .isDeleted(false)
                .build());
    }

    private LocalDateTime oneHourAgo() {
        return LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
    }

    @Test
    void shouldSyncUserDataWithChatIdsAndNewMessages() {
        Message message = createMessage(user1.getUserId(), "update me");

        var response = syncService.syncUserData(user1.getUserId(), oneHourAgo());

        assertThat(response.getSyncTime()).isNotNull();
        assertThat(response.getChatIds()).containsExactly(chat.getChatId());
        assertThat(response.getUpdatedChats()).extracting(Chat::getChatId)
                .containsExactly(chat.getChatId());
        assertThat(response.getNewMessages().get(chat.getChatId()))
                .extracting(Message::getMessageId)
                .containsExactly(message.getMessageId());
    }

    @Test
    void shouldExcludeChatsNotUpdatedSinceLastSync() {
        createMessage(user1.getUserId(), "old");

        var response = syncService.syncUserData(user1.getUserId(), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        assertThat(response.getUpdatedChats()).isEmpty();
        assertThat(response.getNewMessages()).isEmpty();
    }

    @Test
    void shouldSyncMessagesOnly() {
        Message message = createMessage(user1.getUserId(), "only messages");

        var response = syncService.syncMessagesOnly(user2.getUserId(), oneHourAgo());

        assertThat(response.getNewMessages().get(chat.getChatId()))
                .extracting(Message::getMessageId)
                .containsExactly(message.getMessageId());
    }

    @Test
    void shouldSyncChatsOnly() {
        var response = syncService.syncChatsOnly(user1.getUserId(), oneHourAgo());

        assertThat(response.getUpdatedChats()).extracting(Chat::getChatId)
                .containsExactly(chat.getChatId());
        assertThat(response.getNewMessages()).isNull();
    }

    @Test
    void shouldGetSyncStatus() {
        createMessage(user1.getUserId(), "status message");
        createMessage(user1.getUserId(), "another");

        var status = syncService.getSyncStatus(user1.getUserId());

        assertThat(status.totalChats()).isEqualTo(1);
        assertThat(status.totalMessages()).isEqualTo(2);
        assertThat(status.pendingUploads()).isZero();
        assertThat(status.lastMessagesSync()).isNotNull();
    }
}