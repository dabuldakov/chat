package com.chat.server.integration;

import com.chat.server.entity.User;
import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.NotFoundException;
import com.chat.server.exception.UnauthorizedException;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.AuthService;
import com.chat.server.service.EmailService;
import com.chat.server.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class AuthServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserSessionService userSessionService;

    @MockitoBean
    private EmailService emailService;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("alice")
                .email("alice@example.com")
                .passwordHash(passwordEncoder.encode("oldPassword123"))
                .status(User.UserStatus.ACTIVE)
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    @Test
    void shouldAuthenticateWithValidCredentials() {
        var authenticated = authService.authenticate(user.getUsername(), "oldPassword123");

        assertThat(authenticated.getUserId()).isEqualTo(user.getUserId());
    }

    @Test
    void shouldRejectWrongPassword() {
        assertThatThrownBy(() -> authService.authenticate("alice", "wrong"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void shouldRejectUnknownUsername() {
        assertThatThrownBy(() -> authService.authenticate("ghost", "oldPassword123"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void shouldRejectInactiveAccount() {
        user.setStatus(User.UserStatus.INACTIVE);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.authenticate(user.getUsername(), "oldPassword123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Account is not active");
    }

    @Test
    void shouldRejectDeletedAccount() {
        user.setIsDeleted(true);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.authenticate(user.getUsername(), "oldPassword123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void shouldChangePasswordAndInvalidateSessions() {
        userSessionService.createSession(user.getUserId(), "token1", "refresh1",
                "device-1", "Pixel", "ANDROID", null, null);

        authService.changePassword(user.getUserId(), "oldPassword123", "newPassword456");

        assertThatThrownBy(() -> authService.authenticate(user.getUsername(), "oldPassword123"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(authService.authenticate(user.getUsername(), "newPassword456")).isNotNull();
        assertThat(userSessionService.getActiveSessionsCount(user.getUserId())).isZero();
    }

    @Test
    void shouldRejectChangePasswordWithWrongOldPassword() {
        assertThatThrownBy(() -> authService.changePassword(user.getUserId(), "wrong", "new"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void shouldRejectChangePasswordForMissingUser() {
        assertThatThrownBy(() -> authService.changePassword(99999L, "old", "new"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldSendPasswordResetToken() {
        authService.sendPasswordResetToken(user.getEmail());

        User reloaded = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getResetToken()).isNotBlank();
        assertThat(reloaded.getResetTokenExpiry()).isAfter(LocalDateTime.now());
        verify(emailService).sendPasswordResetEmail(Mockito.eq(user.getEmail()), Mockito.anyString());
    }

    @Test
    void shouldThrowNotFoundForUnknownResetEmail() {
        assertThatThrownBy(() -> authService.sendPasswordResetToken("nobody@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldResetPasswordWithValidToken() {
        user.setResetToken("reset-token-123");
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        authService.resetPassword("reset-token-123", "brandNewPass");

        User reloaded = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getResetToken()).isNull();
        assertThat(reloaded.getResetTokenExpiry()).isNull();
        assertThat(authService.authenticate(user.getUsername(), "brandNewPass")).isNotNull();
    }

    @Test
    void shouldRejectExpiredResetToken() {
        user.setResetToken("expired-token");
        user.setResetTokenExpiry(LocalDateTime.now().minusMinutes(5));
        userRepository.save(user);

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "new"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void shouldRejectInvalidResetToken() {
        assertThatThrownBy(() -> authService.resetPassword("no-such-token", "new"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid or expired token");
    }

    @Test
    void shouldVerifyEmailWithValidToken() {
        user.setEmailVerificationToken("verification-token");
        userRepository.save(user);

        authService.verifyEmail("verification-token");

        User reloaded = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getEmailVerified()).isTrue();
        assertThat(reloaded.getEmailVerificationToken()).isNull();
    }

    @Test
    void shouldRejectInvalidVerificationToken() {
        assertThatThrownBy(() -> authService.verifyEmail("no-such-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid verification token");
    }

    @Test
    void shouldSendEmailVerification() {
        authService.sendEmailVerification(user.getUserId());

        User reloaded = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloaded.getEmailVerificationToken()).isNotBlank();
        verify(emailService).sendEmailVerification(Mockito.eq(user.getEmail()), Mockito.anyString());
    }

    @Test
    void shouldThrowNotFoundForMissingUserOnEmailVerification() {
        assertThatThrownBy(() -> authService.sendEmailVerification(99999L))
                .isInstanceOf(NotFoundException.class);
    }
}