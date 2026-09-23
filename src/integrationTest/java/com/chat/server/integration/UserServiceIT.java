package com.chat.server.integration;

import com.chat.server.user.UpdateProfileRequestDto;
import com.chat.server.user.User;
import com.chat.server.exception.ConflictException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.user.UserRepository;
import com.chat.server.user.UserService;
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
                .email("alice@example.com")
                .passwordHash("hash")
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
    void shouldCreateUserWithEncodedPassword() {
        var created = userService.createUser("bob", "bob@example.com", "secret123");

        assertThat(created.getUserId()).isNotNull();
        assertThat(created.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(created.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(created.getIsDeleted()).isFalse();
    }

    @Test
    void shouldRejectDuplicateUsername() {
        assertThatThrownBy(() -> userService.createUser("alice", "other@example.com", "secret123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void shouldRejectDuplicateEmail() {
        assertThatThrownBy(() -> userService.createUser("bob", "alice@example.com", "secret123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already exists");
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
        assertThatThrownBy(() -> userService.findByEmail("missing@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldGetUserIdByUuid() {
        assertThat(userService.getUserIdByUuid(user.getUserUuid())).isEqualTo(user.getUserId());
    }

    @Test
    void shouldUpdateProfile() {
        UpdateProfileRequestDto request = profileRequest();
        request.setUsername("alice2");

        var updated = userService.updateUser(user.getUserId(), request);

        assertThat(updated.getUsername()).isEqualTo("alice2");
        assertThat(updated.getFirstName()).isEqualTo("Alice");
        assertThat(updated.getLastName()).isEqualTo("Smith");
    }

    @Test
    void shouldRejectTakenUsernameOrEmailOnUpdate() {
        userRepository.save(User.builder()
                .username("bob")
                .email("bob@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());

        UpdateProfileRequestDto usernameRequest = new UpdateProfileRequestDto();
        usernameRequest.setUsername("bob");
        assertThatThrownBy(() -> userService.updateUser(user.getUserId(), usernameRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");

        UpdateProfileRequestDto emailRequest = new UpdateProfileRequestDto();
        emailRequest.setEmail("bob@example.com");
        assertThatThrownBy(() -> userService.updateUser(user.getUserId(), emailRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");
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
    void shouldUpdateOnlineStatus() {
        assertThat(userService.isUserOnline(user.getUserUuid())).isFalse();

        userService.updateOnlineStatus(user.getUserId(), true);

        assertThat(userService.isUserOnline(user.getUserUuid())).isTrue();
    }

    @Test
    void shouldSearchUsers() {
        userService.createUser("alicebob", "alicebob@example.com", "secret123");

        var result = userService.searchUsers("alice", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactlyInAnyOrder("alice", "alicebob");
    }

    @Test
    void shouldSoftDeleteUser() {
        userService.deleteUser(user.getUserId());

        assertThatThrownBy(() -> userService.getUserById(user.getUserId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(userRepository.findAll()).isEmpty();
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