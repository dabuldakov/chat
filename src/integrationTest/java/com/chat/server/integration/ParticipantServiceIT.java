package com.chat.server.integration;

import com.chat.server.entity.Chat;
import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.ChatRepository;
import com.chat.server.repository.ParticipantRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.ChatService;
import com.chat.server.service.ParticipantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private ChatService chatService;

    private User creator;
    private User member;
    private User thirdUser;
    private User outsider;

    @BeforeEach
    void setUp() {
        creator = createUser("creator");
        member = createUser("member");
        thirdUser = createUser("third");
        outsider = createUser("outsider");
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

    private Chat groupChat() {
        var response = chatService.createGroupChat("Group", creator.getUserId(),
                List.of(member.getUserUuid()));
        return chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
    }

    private Chat privateChat() {
        var response = chatService.createPrivateChat(creator.getUserId(), member.getUserUuid());
        return chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
    }

    @Test
    void shouldGetChatParticipantsAndDetails() {
        Chat chat = groupChat();

        List<Participant> participants = participantService.getChatParticipants(chat.getChatId());

        assertThat(participants).hasSize(2);
        assertThat(participants).extracting(Participant::getUserId)
                .containsExactlyInAnyOrder(creator.getUserId(), member.getUserId());
    }

    @Test
    void shouldGetChatParticipantsWithDetails() {
        Chat chat = groupChat();

        participantService.addParticipantsToGroup(chat.getChatId(), List.of(thirdUser.getUserId()), creator.getUserId());

        var details = participantService.getChatParticipantsWithDetails(chat.getChatId());

        assertThat(details).hasSize(3);
        assertThat(details).filteredOn(d -> d.getUserUuid().equals(thirdUser.getUserUuid()))
                .singleElement()
                .extracting(d -> d.getUsername())
                .isEqualTo("third");
    }

    @Test
    void shouldAddParticipantsToGroup() {
        Chat chat = groupChat();

        participantService.addParticipantsToGroup(chat.getChatId(), List.of(thirdUser.getUserId()), creator.getUserId());

        assertThat(participantService.getParticipantsCount(chat.getChatId())).isEqualTo(3);
        assertThat(participantRepository.existsByChatIdAndUserId(chat.getChatId(), thirdUser.getUserId())).isTrue();
    }

    @Test
    void shouldAddParticipantIdempotently() {
        Chat chat = groupChat();

        participantService.addParticipantsToGroup(chat.getChatId(), List.of(thirdUser.getUserId()), creator.getUserId());
        participantService.addParticipantsToGroup(chat.getChatId(), List.of(thirdUser.getUserId()), creator.getUserId());

        assertThat(participantService.getParticipantsCount(chat.getChatId())).isEqualTo(3);
    }

    @Test
    void shouldRejectAddingParticipantsToPrivateChat() {
        Chat chat = privateChat();

        assertThatThrownBy(() -> participantService.addParticipantsToGroup(
                chat.getChatId(), List.of(thirdUser.getUserId()), creator.getUserId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("group chats");
    }

    @Test
    void shouldRejectAddingByNonParticipant() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.addParticipantsToGroup(
                chat.getChatId(), List.of(thirdUser.getUserId()), outsider.getUserId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectAddingByNonCreatorMember() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.addParticipantsToGroup(
                chat.getChatId(), List.of(thirdUser.getUserId()), member.getUserId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only group creator");
    }

    @Test
    void shouldRemoveParticipantAsCreator() {
        Chat chat = groupChat();

        participantService.removeParticipantFromGroup(chat.getChatId(), member.getUserId(), creator.getUserId());

        assertThat(participantRepository.existsByChatIdAndUserId(chat.getChatId(), member.getUserId())).isFalse();
        assertThat(participantService.getParticipantsCount(chat.getChatId())).isEqualTo(1);
    }

    @Test
    void shouldRejectRemoveByNonCreator() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.removeParticipantFromGroup(
                chat.getChatId(), member.getUserId(), member.getUserId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only group creator");
    }

    @Test
    void shouldRejectRemovingGroupCreator() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.removeParticipantFromGroup(
                chat.getChatId(), creator.getUserId(), creator.getUserId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot remove group creator");
    }

    @Test
    void shouldRejectRemovingNonParticipant() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.removeParticipantFromGroup(
                chat.getChatId(), thirdUser.getUserId(), creator.getUserId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldLeaveGroup() {
        Chat chat = groupChat();

        participantService.leaveGroup(chat.getChatId(), member.getUserId());

        assertThat(participantRepository.existsByChatIdAndUserId(chat.getChatId(), member.getUserId())).isFalse();
        assertThat(participantService.getUserChatIds(member.getUserId())).isEmpty();
    }

    @Test
    void shouldRejectCreatorLeavingGroup() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.leaveGroup(chat.getChatId(), creator.getUserId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Group creator cannot leave");
    }

    @Test
    void shouldRejectLeavingPrivateChat() {
        Chat chat = privateChat();

        assertThatThrownBy(() -> participantService.leaveGroup(chat.getChatId(), member.getUserId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot leave private chat");
    }

    @Test
    void shouldUpdateParticipantRole() {
        Chat chat = groupChat();

        participantService.updateParticipantRole(chat.getChatId(), member.getUserId(), creator.getUserId(),
                Participant.ParticipantRole.ADMIN);

        Participant updated = participantService.getParticipant(chat.getChatId(), member.getUserId());
        assertThat(updated.getRole()).isEqualTo(Participant.ParticipantRole.ADMIN);
    }

    @Test
    void shouldRejectRoleChangeByNonCreator() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.updateParticipantRole(
                chat.getChatId(), member.getUserId(), member.getUserId(), Participant.ParticipantRole.ADMIN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldRejectRoleChangeOfCreator() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.updateParticipantRole(
                chat.getChatId(), creator.getUserId(), creator.getUserId(), Participant.ParticipantRole.MEMBER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldMuteAndUnmuteChat() {
        Chat chat = groupChat();

        participantService.muteChat(chat.getChatId(), member.getUserId(), 24);
        assertThat(participantService.getParticipant(chat.getChatId(), member.getUserId()).isMuted()).isTrue();

        participantService.unmuteChat(chat.getChatId(), member.getUserId());
        assertThat(participantService.getParticipant(chat.getChatId(), member.getUserId()).isMuted()).isFalse();
    }

    @Test
    void shouldPinAndUnpinChat() {
        Chat chat = groupChat();

        participantService.pinChat(chat.getChatId(), member.getUserId());
        assertThat(participantService.getParticipant(chat.getChatId(), member.getUserId()).getIsPinned()).isTrue();

        participantService.unpinChat(chat.getChatId(), member.getUserId());
        assertThat(participantService.getParticipant(chat.getChatId(), member.getUserId()).getIsPinned()).isFalse();
    }

    @Test
    void shouldGetUserChatIds() {
        Chat group = groupChat();
        Chat priv = privateChat();

        assertThat(participantService.getUserChatIds(creator.getUserId()))
                .containsExactlyInAnyOrder(group.getChatId(), priv.getChatId());
        assertThat(participantService.getUserChatIds(member.getUserId()))
                .containsExactlyInAnyOrder(group.getChatId(), priv.getChatId());
    }

    @Test
    void shouldReturnParticipantNotFound() {
        Chat chat = groupChat();

        assertThatThrownBy(() -> participantService.getParticipant(chat.getChatId(), thirdUser.getUserId()))
                .isInstanceOf(NotFoundException.class);
    }
}