package com.chat.server.integration;

import com.chat.server.contacts.AddContactRequestDto;
import com.chat.server.contacts.ContactService;
import com.chat.server.conversation.Chat;
import com.chat.server.conversation.ChatRepository;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.ParticipantCandidateDto;
import com.chat.server.conversation.ParticipantService;
import com.chat.server.exception.AccessDeniedException;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Список пользователей, которых можно добавить в группу.
 *
 * <p>Регрессия: в список попадали только контакты, поэтому человека, с которым
 * уже есть личная переписка, но который не сохранён в контактах (или сохранён
 * устаревшей записью), нельзя было добавить в группу.
 */
class ParticipantCandidatesIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private ContactService contactService;

    private User owner;
    private User member;
    private User contactUser;
    private User chatPartner;
    private User outsider;
    private Chat group;

    @BeforeEach
    void setUp() {
        owner = createUser("owner");
        member = createUser("member");
        contactUser = createUser("contact_user");
        chatPartner = createUser("chat_partner");
        outsider = createUser("outsider");

        group = groupChat("Group", owner, List.of(member));
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private Chat groupChat(String title, User creator, List<User> members) {
        var response = chatService.createGroupChat(title, creator.getUserId(),
                members.stream().map(User::getUserUuid).toList());
        return chatRepository.findByChatUuid(response.getChatUuid()).orElseThrow();
    }

    private void addContact(User ownerUser, User target) {
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(target.getUserUuid());
        contactService.addContact(ownerUser.getUserId(), request);
    }

    private void privateChat(User a, User b) {
        chatService.createPrivateChat(a.getUserId(), b.getUserUuid());
    }

    private List<ParticipantCandidateDto> candidates() {
        return participantService.getCandidates(group.getChatId(), owner.getUserId());
    }

    @Test
    void contactIsOfferedAndMarkedAsContact() {
        addContact(owner, contactUser);

        assertThat(candidates())
                .extracting(ParticipantCandidateDto::getUserUuid)
                .contains(contactUser.getUserUuid());
        assertThat(candidates())
                .filteredOn(c -> c.getUserUuid().equals(contactUser.getUserUuid()))
                .singleElement()
                .satisfies(c -> assertThat(c.isContact()).isTrue());
    }

    @Test
    void privateChatPartnerIsOfferedEvenWithoutContact() {
        privateChat(owner, chatPartner);

        assertThat(candidates())
                .extracting(ParticipantCandidateDto::getUserUuid)
                .contains(chatPartner.getUserUuid());
        assertThat(candidates())
                .filteredOn(c -> c.getUserUuid().equals(chatPartner.getUserUuid()))
                .singleElement()
                .satisfies(c -> assertThat(c.isContact()).isFalse());
    }

    @Test
    void existingParticipantsAndRequesterAreExcluded() {
        addContact(owner, member);

        assertThat(candidates())
                .extracting(ParticipantCandidateDto::getUserUuid)
                .doesNotContain(member.getUserUuid(), owner.getUserUuid());
    }

    @Test
    void participantsOfGroupChatsAreNotTreatedAsPrivatePartners() {
        // Группа, в которой состоит только outsider, не делает его собеседником.
        groupChat("Other", outsider, List.of(owner));

        assertThat(candidates())
                .extracting(ParticipantCandidateDto::getUserUuid)
                .doesNotContain(outsider.getUserUuid());
    }

    @Test
    void requesterMustBeParticipantOfTheChat() {
        assertThatThrownBy(() -> participantService.getCandidates(
                group.getChatId(), outsider.getUserId()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
