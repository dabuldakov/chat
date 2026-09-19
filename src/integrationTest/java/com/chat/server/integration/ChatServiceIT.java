package com.chat.server.integration;

import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.ParticipantRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private ChatService chatService;

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
}