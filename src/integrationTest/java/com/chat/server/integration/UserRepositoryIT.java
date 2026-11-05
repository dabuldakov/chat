package com.chat.server.integration;

import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User user(String username) {
        return User.builder()
                .username(username)
                .isOnline(false)
                .isDeleted(false)
                .build();
    }

    @Test
    void shouldFindUserByUsername() {
        User saved = userRepository.save(user("alice"));

        var found = userRepository.findByUsername("alice");

        assertThat(saved.getUserId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldCheckUsernameExistence() {
        userRepository.save(user("bob"));

        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByUsername("missing")).isFalse();
    }

    @Test
    void shouldExcludeSoftDeletedUsersFromQueries() {
        userRepository.save(user("alice"));
        userRepository.save(User.builder()
                .username("deleted")
                .isOnline(false)
                .isDeleted(true)
                .build());

        assertThat(userRepository.findAll())
                .extracting(User::getUsername)
                .containsExactly("alice");

        assertThat(userRepository.findByUsername("deleted")).isEmpty();
    }

    @Test
    void shouldSearchUsersByUsernameOrName() {
        userRepository.save(user("john.doe"));
        userRepository.save(user("jane"));

        var result = userRepository.searchByUsernameOrName("john", PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getUsername)
                .containsExactly("john.doe");
    }

    @Test
    void shouldFindOnlineUsers() {
        userRepository.save(user("online1"));
        userRepository.save(User.builder()
                .username("online2")
                .isOnline(true)
                .isDeleted(false)
                .build());

        assertThat(userRepository.findOnlineUsers())
                .extracting(User::getUsername)
                .containsExactly("online2");
    }
}
