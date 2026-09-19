package com.chat.server.integration;

import com.chat.server.entity.User;
import com.chat.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User user(String username, String email) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build();
    }

    @Test
    void shouldFindUserByUsername() {
        User saved = userRepository.save(user("alice", "alice@example.com"));

        var found = userRepository.findByUsername("alice");

        assertThat(saved.getUserId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldCheckEmailExistence() {
        userRepository.save(user("bob", "bob@example.com"));

        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void shouldExcludeSoftDeletedUsersFromQueries() {
        userRepository.save(user("alice", "alice@example.com"));
        userRepository.save(User.builder()
                .username("deleted")
                .email("deleted@example.com")
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(true)
                .build());

        assertThat(userRepository.findAll())
                .extracting(User::getUsername)
                .containsExactly("alice");

        assertThat(userRepository.findByUsername("deleted")).isEmpty();
        assertThat(userRepository.findByEmail("deleted@example.com")).isEmpty();
    }

    @Test
    void shouldSearchUsersByUsernameOrEmail() {
        userRepository.save(user("john.doe", "john@example.com"));
        userRepository.save(user("jane", "jane@other.com"));

        var result = userRepository.searchByUsernameOrEmail("john", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getUsername)
                .containsExactly("john.doe");
    }

    @Test
    void shouldFindOnlineUsers() {
        userRepository.save(user("online1", "online1@example.com"));
        userRepository.save(User.builder()
                .username("online2")
                .email("online2@example.com")
                .passwordHash("hash")
                .isOnline(true)
                .isDeleted(false)
                .build());

        assertThat(userRepository.findOnlineUsers())
                .extracting(User::getUsername)
                .containsExactly("online2");
    }
}