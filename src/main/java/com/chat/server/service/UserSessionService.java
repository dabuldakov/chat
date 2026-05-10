package com.chat.server.service;

import com.chat.server.dto.response.UserSessionDto;
import com.chat.server.entity.UserSession;
import com.chat.server.exception.UnauthorizedException;
import com.chat.server.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    @Transactional
    public UserSession createSession(Long userId, String token, String refreshToken,
                                     String deviceId, String deviceName, String deviceType,
                                     String ipAddress, String userAgent) {
        log.info("Creating session for user: {}, device: {}", userId, deviceId);

        // Инвалидируем старую сессию для этого устройства
        if (deviceId != null) {
            userSessionRepository.invalidateSessionByDeviceId(userId, deviceId);
        }

        UserSession session = UserSession.builder()
                .userId(userId)
                .token(token)
                .refreshToken(refreshToken)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .deviceType(deviceType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastActivity(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        return userSessionRepository.save(session);
    }

    @Transactional
    public UserSession refreshSession(String refreshToken) {
        log.info("Refreshing session with token");

        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!session.getIsActive() || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Session expired");
        }

        session.setLastActivity(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(30));

        return userSessionRepository.save(session);
    }

    @Transactional
    public void invalidateSession(String token) {
        userSessionRepository.findByToken(token)
                .ifPresent(session -> {
                    session.setIsActive(false);
                    userSessionRepository.save(session);
                    log.info("Session invalidated for user: {}", session.getUserId());
                });
    }

    @Transactional
    public void invalidateAllSessions(Long userId) {
        userSessionRepository.invalidateAllSessions(userId);
        log.info("All sessions invalidated for user: {}", userId);
    }

    @Transactional
    public void invalidateSessionByDeviceId(Long userId, String deviceId) {
        userSessionRepository.invalidateSessionByDeviceId(userId, deviceId);
        log.info("Session invalidated for user: {}, device: {}", userId, deviceId);
    }

    @Transactional
    public void updateActivity(String token) {
        userSessionRepository.updateLastActivity(token, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean isSessionValid(String token) {
        return userSessionRepository.findByToken(token)
                .map(session -> session.getIsActive() && session.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> getUserSessions(Long userId) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

        return sessions.stream()
                .map(s -> UserSessionDto.builder()
                        .sessionId(s.getSessionId())
                        .deviceId(s.getDeviceId())
                        .deviceName(s.getDeviceName())
                        .deviceType(s.getDeviceType())
                        .ipAddress(s.getIpAddress())
                        .lastActivity(s.getLastActivity())
                        .isCurrent(false)
                        .build())
                .collect(Collectors.toList());
    }
}