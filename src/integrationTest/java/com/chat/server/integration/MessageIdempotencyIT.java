package com.chat.server.integration;

import com.chat.server.conversation.Chat;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageRepository;
import com.chat.server.conversation.MessageService;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Повторная отправка с тем же clientMessageId не создаёт дубль. */
class MessageIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private MessageRepository messageRepository;

    private User user1;
    private User user2;
    private Chat chat;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(User.builder()
                .username("idem_u1").isOnline(false).isDeleted(false).build());
        user2 = userRepository.save(User.builder()
                .username("idem_u2").isOnline(false).isDeleted(false).build());
        var response = chatService.createPrivateChat(user1.getUserId(), user2.getUserUuid());
        chat = chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
    }

    private Message send(User sender, UUID clientId) {
        return messageService.sendMessage(chat.getChatId(), sender.getUserId(),
                "hello", Message.MessageType.TEXT, null, null, clientId);
    }

    @Test
    void sameClientMessageIdReturnsTheSameMessage() {
        UUID clientId = UUID.randomUUID();

        Message first = send(user1, clientId);
        Message second = send(user1, clientId);

        assertThat(second.getMessageUuid()).isEqualTo(first.getMessageUuid());
        assertThat(messageRepository.findMessagesByChatId(chat.getChatId(),
                org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void differentClientMessageIdsCreateDifferentMessages() {
        Message first = send(user1, UUID.randomUUID());
        Message second = send(user1, UUID.randomUUID());

        assertThat(second.getMessageUuid()).isNotEqualTo(first.getMessageUuid());
    }

    @Test
    void clientMessageIdCannotBeReusedByAnotherUser() {
        UUID clientId = UUID.randomUUID();
        send(user1, clientId);

        assertThatThrownBy(() -> send(user2, clientId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void messagesWithoutClientMessageIdAreNotDeduplicated() {
        Message first = send(user1, null);
        Message second = send(user1, null);

        assertThat(second.getMessageUuid()).isNotEqualTo(first.getMessageUuid());
    }
}
