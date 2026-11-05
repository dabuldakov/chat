package com.chat.server.integration;

import com.chat.server.identity.UpdateProfileRequestDto;
import com.chat.server.identity.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.identity.UserRepository;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("alice")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private UpdateProfileRequestDto profileRequest() {
        UpdateProfileRequestDto request = new UpdateProfileRequestDto();
        request.setFirstName("Alice");
        request.setLastName("Smith");
        return request;
    }

    @Test
    void shouldCreateActiveUserWithoutIdpLinkYet() {
        var created = userService.createUser("bob");

        assertThat(created.getUserId()).isNotNull();
        // Паролей у chat больше нет: вход только через IdP, поэтому auth_subject
        // заполнится позднее при первом OIDC-входе.
        assertThat(created.getAuthSubject()).isNull();
        assertThat(created.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(created.getIsDeleted()).isFalse();
    }

    @Test
    void shouldRejectDuplicateUsername() {
        assertThatThrownBy(() -> userService.createUser("alice"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void shouldGetUserByIdAndByUuid() {
        var byId = userService.getUserById(user.getUserId());
        var byUuid = userService.getUserByUuid(user.getUserUuid());

        assertThat(byId.getUsername()).isEqualTo("alice");
        assertThat(byUuid.getUserId()).isEqualTo(user.getUserId());
    }

    @Test
    void shouldThrowNotFoundForMissingUser() {
        assertThatThrownBy(() -> userService.getUserById(99999L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.getUserByUuid(java.util.UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.findByUsername("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldGetUserIdByUuid() {
        assertThat(userService.getUserIdByUuid(user.getUserUuid())).isEqualTo(user.getUserId());
    }

    @Test
    void shouldUpdateProfile() {
        UpdateProfileRequestDto request = profileRequest();

        var updated = userService.updateUser(user.getUserId(), request);

        assertThat(updated.getFirstName()).isEqualTo("Alice");
        assertThat(updated.getLastName()).isEqualTo("Smith");
        // Username — идентификатор IdP, локально не меняется.
        assertThat(updated.getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldUpdateAndDeleteAvatar() {
        userService.updateAvatar(user.getUserId(), "http://example.com/avatar.png");

        assertThat(userService.getUserById(user.getUserId()).getAvatarUrl())
                .isEqualTo("http://example.com/avatar.png");

        userService.deleteAvatar(user.getUserId());

        assertThat(userService.getUserById(user.getUserId()).getAvatarUrl()).isNull();
    }

    @Test
    void shouldSearchUsers() {
        userService.createUser("alicebob");

        var result = userService.searchUsers("alice", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactlyInAnyOrder("alice", "alicebob");
    }

    @Test
    void shouldLoadUserByUsernameForSecurity() {
        var details = userService.loadUserByUsername(user.getUserUuid().toString());

        assertThat(details.getUsername()).isEqualTo(user.getUserId().toString());
        assertThat(details.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldRejectInvalidUserIdForSecurity() {
        assertThatThrownBy(() -> userService.loadUserByUsername("not-a-uuid"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
