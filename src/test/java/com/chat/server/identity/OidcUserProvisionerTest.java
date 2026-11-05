package com.chat.server.identity;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcUserProvisionerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OidcProperties properties =
            new OidcProperties(true, "iss", "jwks", java.util.List.of("chat-muzea"), true);
    private final OidcUserProvisioner provisioner =
            new OidcUserProvisioner(userRepository, properties);

    @Test
    void returnsExistingUserBySubject() {
        UUID subject = UUID.randomUUID();
        User existing = user(1L, "existing");
        when(userRepository.findByAuthSubject(subject)).thenReturn(Optional.of(existing));

        assertThat(provisioner.findOrCreate(subject, "ignored")).isSameAs(existing);
    }

    @Test
    void linksExistingAccountByUsername() {
        UUID subject = UUID.randomUUID();
        User existing = user(4L, "legacylogin");
        when(userRepository.findByAuthSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByUsername("legacylogin")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = provisioner.findOrCreate(subject, "legacylogin");

        assertThat(result.getAuthSubject()).isEqualTo(subject);
    }

    @Test
    void provisionsNewUserWhenUnknown() {
        UUID subject = UUID.randomUUID();
        when(userRepository.findByAuthSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = provisioner.findOrCreate(subject, "newuser");

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getAuthSubject()).isEqualTo(subject);
        assertThat(result.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
    }

    @Test
    void suffixesUsernameOnCollision() {
        UUID subject = UUID.randomUUID();
        when(userRepository.findByAuthSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        when(userRepository.existsByUsername("taken_" + subject.toString().replace("-", "").substring(0, 6)))
                .thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = provisioner.findOrCreate(subject, "taken");

        assertThat(result.getUsername()).startsWith("taken_").hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void usesDefaultUsernameWhenUsernameMissing() {
        UUID subject = UUID.randomUUID();
        when(userRepository.findByAuthSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("user")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = provisioner.findOrCreate(subject, null);

        assertThat(result.getUsername()).isEqualTo("user");
    }

    @Test
    void recoversFromConcurrentCreation() {
        UUID subject = UUID.randomUUID();
        User concurrent = user(3L, "racer");
        when(userRepository.findByAuthSubject(subject))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        User result = provisioner.findOrCreate(subject, "racer");

        assertThat(result).isSameAs(concurrent);
    }

    private User user(Long id, String username) {
        User user = User.builder()
                .username(username)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setUserId(id);
        return user;
    }
}
