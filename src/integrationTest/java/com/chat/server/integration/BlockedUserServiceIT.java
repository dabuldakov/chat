package com.chat.server.integration;

import com.chat.server.dto.request.AddContactRequestDto;
import com.chat.server.dto.request.BlockUserRequestDto;
import com.chat.server.entity.BlockedUser;
import com.chat.server.entity.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.repository.BlockedUserRepository;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.BlockedUserService;
import com.chat.server.service.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockedUserServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockedUserRepository blockedUserRepository;
    @Autowired
    private BlockedUserService blockedUserService;
    @Autowired
    private ContactService contactService;

    private User user;
    private User target;

    @BeforeEach
    void setUp() {
        user = createUser("owner");
        target = createUser("target");
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

    private BlockUserRequestDto blockRequest(User blocked) {
        BlockUserRequestDto request = new BlockUserRequestDto();
        request.setBlockUserUuid(blocked.getUserUuid());
        request.setReason("spam");
        return request;
    }

    @Test
    void shouldBlockUser() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        assertThat(block.getBlockId()).isNotNull();
        assertThat(block.getUserId()).isEqualTo(user.getUserId());
        assertThat(block.getBlockedUserId()).isEqualTo(target.getUserId());
        assertThat(blockedUserRepository.existsByUserIdAndBlockedUserId(user.getUserId(), target.getUserId())).isTrue();
    }

    @Test
    void shouldRejectBlockingYourself() {
        assertThatThrownBy(() -> blockedUserService.blockUser(user.getUserId(), blockRequest(user)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot block yourself");
    }

    @Test
    void shouldRejectDuplicateBlock() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        assertThatThrownBy(() -> blockedUserService.blockUser(user.getUserId(), blockRequest(target)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already blocked");
    }

    @Test
    void shouldRemoveContactWhenBlocking() {
        contactService.addContact(user.getUserId(), contactRequest(target));

        assertThat(contactService.isContact(user.getUserId(), target.getUserId())).isTrue();

        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        assertThat(contactService.isContact(user.getUserId(), target.getUserId())).isFalse();
    }

    private AddContactRequestDto contactRequest(User contact) {
        AddContactRequestDto request = new AddContactRequestDto();
        request.setContactUserUuid(contact.getUserUuid());
        return request;
    }

    @Test
    void shouldReturnBlockedUsers() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        var blocked = blockedUserService.getBlockedUsers(user.getUserId());

        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).getBlockedUserUuid()).isEqualTo(target.getUserUuid());
        assertThat(blocked.get(0).getUsername()).isEqualTo("target");
    }

    @Test
    void shouldReturnBlockedUsersPaginated() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        var page = blockedUserService.getBlockedUsersPaginated(user.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getBlockedUserId()).isEqualTo(target.getUserId());
    }

    @Test
    void shouldSearchBlockedUsers() {
        User other = createUser("vendor");
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));
        blockedUserService.blockUser(user.getUserId(), blockRequest(other));

        var result = blockedUserService.searchBlockedUsers(user.getUserId(), "vendor", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("vendor");
    }

    @Test
    void shouldGetBlockByUuid() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        var dto = blockedUserService.getBlockByUuid(user.getUserId(), block.getBlockUuid());

        assertThat(dto.getBlockUuid()).isEqualTo(block.getBlockUuid());
    }

    @Test
    void shouldRejectGetBlockByUuidForAnotherUser() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        User stranger = createUser("stranger");
        assertThatThrownBy(() -> blockedUserService.getBlockByUuid(stranger.getUserId(), block.getBlockUuid()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldCheckIsBlockedAndMutualBlock() {
        assertThat(blockedUserService.isBlocked(user.getUserId(), target.getUserId())).isFalse();
        assertThat(blockedUserService.isMutualBlock(user.getUserId(), target.getUserId())).isFalse();

        blockedUserService.blockUser(user.getUserId(), blockRequest(target));
        assertThat(blockedUserService.isBlocked(user.getUserId(), target.getUserId())).isTrue();

        blockedUserService.blockUser(target.getUserId(), blockRequest(user));
        assertThat(blockedUserService.isMutualBlock(user.getUserId(), target.getUserId())).isTrue();
    }

    @Test
    void shouldCheckCanSendMessage() {
        assertThat(blockedUserService.canSendMessage(user.getUserId(), target.getUserId())).isTrue();

        blockedUserService.blockUser(user.getUserId(), blockRequest(target));
        assertThat(blockedUserService.canSendMessage(user.getUserId(), target.getUserId())).isFalse();

        assertThat(blockedUserService.canSendMessage(target.getUserId(), user.getUserId())).isFalse();
    }

    @Test
    void shouldUnblockByUuid() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        blockedUserService.unblockUser(user.getUserId(), block.getBlockUuid());

        assertThat(blockedUserService.isBlocked(user.getUserId(), target.getUserId())).isFalse();
    }

    @Test
    void shouldRejectUnblockOfAnotherUser() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        User stranger = createUser("stranger");
        assertThatThrownBy(() -> blockedUserService.unblockUser(stranger.getUserId(), block.getBlockUuid()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldUnblockByUserId() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        blockedUserService.unblockUserByUserId(user.getUserId(), target.getUserId());

        assertThat(blockedUserService.isBlocked(user.getUserId(), target.getUserId())).isFalse();
    }

    @Test
    void shouldUnblockAllUsers() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));
        blockedUserService.blockUser(user.getUserId(), blockRequest(createUser("third")));

        blockedUserService.unblockAllUsers(user.getUserId());

        assertThat(blockedUserService.getBlockedCount(user.getUserId())).isZero();
    }

    @Test
    void shouldReturnBlockedCountAndIds() {
        blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        assertThat(blockedUserService.getBlockedCount(user.getUserId())).isEqualTo(1);
        assertThat(blockedUserService.getBlockedUserIds(user.getUserId()))
                .containsExactly(target.getUserId());
        assertThat(blockedUserService.getUserIdsWhoBlocked(target.getUserId()))
                .containsExactly(user.getUserId());
    }

    @Test
    void shouldPersistBlockedUser() {
        BlockedUser block = blockedUserService.blockUser(user.getUserId(), blockRequest(target));

        var found = blockedUserRepository.findByBlockUuid(block.getBlockUuid());
        assertThat(found).isPresent();
        assertThat(found.get().getReason()).isEqualTo("spam");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }
}