package com.chat.server.integration;

import com.chat.server.user.User;
import com.chat.server.auth.UserSession;
import com.chat.server.exception.UnauthorizedException;
import com.chat.server.user.UserRepository;
import com.chat.server.auth.UserSessionRepository;
import com.chat.server.auth.TokenHasher;
import com.chat.server.auth.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSessionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSessionRepository userSessionRepository;
    @Autowired
    private UserSessionService userSessionService;
    @Autowired
    private TokenHasher tokenHasher;

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

    private UserSession createSession(String token, String deviceId) {
        return userSessionService.createSession(
                user.getUserId(), token, "refresh-" + token, deviceId,
                "Pixel", "ANDROID", "127.0.0.1", "test-agent");
    }

    @Test
    void shouldCreateSession() {
        UserSession session = createSession("token-1", "device-1");

        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.isValid()).isTrue();
        assertThat(session.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(userSessionService.isSessionValid("token-1")).isTrue();
    }

    @Test
    void shouldInvalidatePreviousSessionForSameDevice() {
        UserSession first = createSession("token-1", "device-1");
        createSession("token-2", "device-1");

        assertThat(userSessionService.isSessionValid("token-1")).isFalse();
        assertThat(userSessionService.isSessionValid("token-2")).isTrue();
        assertThat(userSessionService.getActiveSessionsCount(user.getUserId())).isEqualTo(1);
    }

    @Test
    void shouldRefreshSession() {
        createSession("token-1", "device-1");

        UserSession refreshed = userSessionService.refreshSession("refresh-token-1");

        assertThat(refreshed.isValid()).isTrue();
        assertThat(refreshed.getExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(29));
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        assertThatThrownBy(() -> userSessionService.refreshSession("nope"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void shouldRejectExpiredSessionRefresh() {
        UserSession session = createSession("token-1", "device-1");
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        userSessionRepository.save(session);

        assertThatThrownBy(() -> userSessionService.refreshSession("refresh-token-1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void shouldInvalidateSession() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.invalidateSession("token-1");

        assertThat(userSessionService.isSessionValid("token-1")).isFalse();
        assertThat(userSessionService.isSessionValid("token-2")).isTrue();
    }

    @Test
    void shouldInvalidateAllSessions() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.invalidateAllSessions(user.getUserId());

        assertThat(userSessionService.getActiveSessionsCount(user.getUserId())).isZero();
    }

    @Test
    void shouldInvalidateAllSessionsExceptCurrent() {
        UserSession current = createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.invalidateAllSessionsExceptCurrent(user.getUserId(), current.getSessionId());

        assertThat(userSessionService.isSessionValid("token-1")).isTrue();
        assertThat(userSessionService.isSessionValid("token-2")).isFalse();
    }

    @Test
    void shouldInvalidateSessionByDeviceId() {
        createSession("token-1", "device-1");

        userSessionService.invalidateSessionByDeviceId(user.getUserId(), "device-1");

        assertThat(userSessionService.isSessionValid("token-1")).isFalse();
    }

    @Test
    void shouldUpdateActivityAndFcmToken() {
        UserSession session = createSession("token-1", "device-1");

        userSessionService.updateFcmToken(session.getSessionId(), "fcm-token-123");

        UserSession reloaded = reload("token-1");
        assertThat(reloaded.getFcmToken()).isEqualTo("fcm-token-123");
        assertThat(userSessionService.getActiveFcmTokens(user.getUserId()))
                .containsExactly("fcm-token-123");
    }

    @Test
    void shouldRegisterFcmTokenForMatchingDevice() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.registerFcmToken(user.getUserId(), "device-2", "fcm-device-2");

        assertThat(reload("token-1").getFcmToken()).isNull();
        assertThat(reload("token-2").getFcmToken())
                .isEqualTo("fcm-device-2");
    }

    @Test
    void shouldRegisterFcmTokenForAllSessionsWhenDeviceUnknown() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.registerFcmToken(user.getUserId(), null, "fcm-shared");

        assertThat(userSessionService.getActiveFcmTokens(user.getUserId()))
                .hasSize(2)
                .allMatch("fcm-shared"::equals);
    }

    @Test
    void shouldRejectBlankFcmToken() {
        createSession("token-1", "device-1");

        assertThatThrownBy(() -> userSessionService.registerFcmToken(user.getUserId(), "device-1", "  "))
                .isInstanceOf(com.chat.server.exception.BadRequestException.class);
    }

    @Test
    void shouldGetUserSessions() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        var sessions = userSessionService.getUserSessions(user.getUserId(), "token-1");

        assertThat(sessions).hasSize(2);
        assertThat(sessions).filteredOn(s -> s.isCurrent()).singleElement()
                .extracting(s -> s.getDeviceId())
                .isEqualTo("device-1");
    }

    @Test
    void shouldGetSessionByToken() {
        createSession("token-1", "device-1");

        UserSession found = userSessionService.getSessionByToken("token-1");

        assertThat(found.getUserId()).isEqualTo(user.getUserId());
        assertThatThrownBy(() -> userSessionService.getSessionByToken("missing"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void shouldLogoutFromAllDevices() {
        createSession("token-1", "device-1");
        createSession("token-2", "device-2");

        userSessionService.logoutFromAllDevices(user.getUserId(), null);

        assertThat(userSessionService.getActiveSessionsCount(user.getUserId())).isZero();
    }

    @Test
    void shouldCleanupExpiredSessions() {
        UserSession session = createSession("token-1", "device-1");
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        userSessionRepository.save(session);

        userSessionService.cleanupExpiredSessions();

        assertThat(userSessionRepository.findByToken(tokenHasher.hash("token-1"))).isEmpty();
    }

    private UserSession reload(String token) {
        return userSessionRepository.findByToken(tokenHasher.hash(token)).orElseThrow();
    }
}