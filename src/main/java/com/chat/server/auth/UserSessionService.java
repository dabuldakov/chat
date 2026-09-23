package com.chat.server.auth;

import com.chat.server.exception.BadRequestException;
import com.chat.server.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;
    private final TokenHasher tokenHasher;

    @Transactional
    public UserSession createSession(Long userId, String token, String refreshToken,
                                     String deviceId, String deviceName, String deviceType,
                                     String ipAddress, String userAgent) {
        log.info("Creating session for user: {}, device: {}", userId, deviceId);

        // Инвалидируем старую сессию для этого устройства
        if (deviceId != null && !deviceId.isEmpty()) {
            userSessionRepository.invalidateSessionByDeviceId(userId, deviceId, LocalDateTime.now(ZoneOffset.UTC));
        }

        UserSession session = UserSession.builder()
                .userId(userId)
                .token(tokenHasher.hash(token))
                .refreshToken(tokenHasher.hash(refreshToken))
                .deviceId(deviceId)
                .deviceName(deviceName)
                .deviceType(deviceType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastActivity(LocalDateTime.now(ZoneOffset.UTC))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(30))
                .isActive(true)
                .build();

        return userSessionRepository.save(session);
    }

    @Transactional
    public UserSession refreshSession(String refreshToken) {
        log.info("Refreshing session with token");

        UserSession session = userSessionRepository.findByRefreshToken(tokenHasher.hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!session.getIsActive() || session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new UnauthorizedException("Session expired");
        }

        session.setLastActivity(LocalDateTime.now(ZoneOffset.UTC));
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(30));

        return userSessionRepository.save(session);
    }

    @Transactional
    public void rotateRefreshToken(Long sessionId, String newRefreshToken) {
        userSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRefreshToken(tokenHasher.hash(newRefreshToken));
            session.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            userSessionRepository.save(session);
        });
    }

    @Transactional
    public void invalidateSession(String token) {
        userSessionRepository.findByToken(tokenHasher.hash(token))
                .ifPresent(session -> {
                    session.setIsActive(false);
                    session.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    userSessionRepository.save(session);
                    log.info("Session invalidated for user: {}", session.getUserId());
                });
    }

    @Transactional
    public void invalidateAllSessions(Long userId) {
        userSessionRepository.invalidateAllSessions(userId, LocalDateTime.now(ZoneOffset.UTC));
        log.info("All sessions invalidated for user: {}", userId);
    }

    @Transactional
    public void invalidateAllSessionsExceptCurrent(Long userId, Long currentSessionId) {
        userSessionRepository.invalidateAllSessionsExceptCurrent(userId, currentSessionId, LocalDateTime.now(ZoneOffset.UTC));
        log.info("All sessions except current invalidated for user: {}", userId);
    }

    @Transactional
    public void invalidateSessionByDeviceId(Long userId, String deviceId) {
        userSessionRepository.invalidateSessionByDeviceId(userId, deviceId, LocalDateTime.now(ZoneOffset.UTC));
        log.info("Session invalidated for user: {}, device: {}", userId, deviceId);
    }

    @Transactional
    public void updateActivity(String token) {
        userSessionRepository.updateLastActivity(tokenHasher.hash(token), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void updateFcmToken(Long sessionId, String fcmToken) {
        userSessionRepository.updateFcmToken(sessionId, fcmToken);
        log.debug("FCM token updated for session: {}", sessionId);
    }

    @Transactional
    public void registerFcmToken(Long userId, String deviceId, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new BadRequestException("FCM token is required");
        }

        if (deviceId != null && !deviceId.isBlank()) {
            userSessionRepository.findActiveSessionByDeviceId(userId, deviceId)
                    .ifPresentOrElse(
                            session -> userSessionRepository.updateFcmToken(session.getSessionId(), fcmToken),
                            () -> userSessionRepository.findActiveSessionsByUserId(userId)
                                    .forEach(s -> userSessionRepository.updateFcmToken(s.getSessionId(), fcmToken)));
        } else {
            userSessionRepository.findActiveSessionsByUserId(userId)
                    .forEach(s -> userSessionRepository.updateFcmToken(s.getSessionId(), fcmToken));
        }
        log.info("FCM token registered for user: {}", userId);
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(String token) {
        return userSessionRepository.findByToken(tokenHasher.hash(token))
                .map(session -> session.getIsActive() && session.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public UserSession getSessionByToken(String token) {
        return userSessionRepository.findByToken(tokenHasher.hash(token))
                .orElseThrow(() -> new UnauthorizedException("Session not found"));
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> getUserSessions(Long userId) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

        // Получаем текущую сессию (ту, которая последняя использовалась)
        String currentToken = getCurrentTokenFromSecurityContext(); // Нужно реализовать

        return sessions.stream()
                .map(s -> UserSessionDto.fromEntity(s, s.getToken().equals(currentToken)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> getUserSessions(Long userId, String currentToken) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);
        String currentHash = tokenHasher.hash(currentToken);

        return sessions.stream()
                .map(s -> UserSessionDto.fromEntity(s, s.getToken().equals(currentHash)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getActiveSessionsCount(Long userId) {
        return userSessionRepository.countActiveSessionsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<String> getActiveFcmTokens(Long userId) {
        return userSessionRepository.findActiveFcmTokensByUserId(userId);
    }

    @Transactional
    public void logoutFromAllDevices(Long userId, Long exceptSessionId) {
        if (exceptSessionId != null) {
            userSessionRepository.invalidateAllSessionsExceptCurrent(userId, exceptSessionId, LocalDateTime.now(ZoneOffset.UTC));
        } else {
            userSessionRepository.invalidateAllSessions(userId, LocalDateTime.now(ZoneOffset.UTC));
        }
        log.info("Logged out from all devices for user: {}", userId);
    }

    // ==================== Очистка старых сессий (шедулер) ====================

    @Scheduled(cron = "0 0 2 * * ?") // Каждый день в 2 часа ночи
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int expiredCount = userSessionRepository.invalidateExpiredSessions(now);
        int deletedCount = userSessionRepository.deleteExpiredSessions(now);
        int oldInactiveCount = userSessionRepository.deleteInactiveSessionsOlderThan(now.minusMonths(3));

        log.info("Session cleanup completed. Expired: {}, Deleted: {}, Old inactive: {}",
                expiredCount, deletedCount, oldInactiveCount);
    }

    private String getCurrentTokenFromSecurityContext() {
        // Реализуйте получение текущего токена из SecurityContext
        // Можно через JwtUtil или сохранение в ThreadLocal
        return null;
    }
}