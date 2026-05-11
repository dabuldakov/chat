package com.chat.server.repository;

import com.chat.server.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    // ==================== Поиск ====================

    Optional<UserSession> findByToken(String token);

    Optional<UserSession> findByRefreshToken(String refreshToken);

    Optional<UserSession> findBySessionUuid(UUID sessionUuid);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.isActive = true ORDER BY s.lastActivity DESC")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.deviceId = :deviceId AND s.isActive = true")
    Optional<UserSession> findActiveSessionByDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.isActive = true AND s.expiresAt > :now")
    List<UserSession> findValidSessionsByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.fcmToken = :fcmToken AND s.isActive = true")
    Optional<UserSession> findByFcmToken(@Param("fcmToken") String fcmToken);

    // ==================== Проверка существования ====================

    boolean existsByToken(String token);

    boolean existsByRefreshToken(String refreshToken);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSession s WHERE s.userId = :userId AND s.deviceId = :deviceId AND s.isActive = true")
    boolean hasActiveSession(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    // ==================== Обновление ====================

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivity = :lastActivity WHERE s.token = :token")
    void updateLastActivity(@Param("token") String token, @Param("lastActivity") LocalDateTime lastActivity);

    @Modifying
    @Query("UPDATE UserSession s SET s.fcmToken = :fcmToken WHERE s.sessionId = :sessionId")
    void updateFcmToken(@Param("sessionId") Long sessionId, @Param("fcmToken") String fcmToken);

    @Modifying
    @Query("UPDATE UserSession s SET s.refreshToken = :newRefreshToken WHERE s.refreshToken = :oldRefreshToken")
    void updateRefreshToken(@Param("oldRefreshToken") String oldRefreshToken,
                            @Param("newRefreshToken") String newRefreshToken);

    // ==================== Инвалидация сессий ====================

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.updatedAt = :now WHERE s.token = :token")
    void invalidateSession(@Param("token") String token, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.updatedAt = :now WHERE s.userId = :userId")
    void invalidateAllSessions(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.updatedAt = :now WHERE s.userId = :userId AND s.deviceId = :deviceId")
    void invalidateSessionByDeviceId(@Param("userId") Long userId,
                                     @Param("deviceId") String deviceId,
                                     @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.updatedAt = :now WHERE s.userId = :userId AND s.sessionId != :currentSessionId")
    void invalidateAllSessionsExceptCurrent(@Param("userId") Long userId,
                                            @Param("currentSessionId") Long currentSessionId,
                                            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.updatedAt = :now WHERE s.expiresAt < :now AND s.isActive = true")
    int invalidateExpiredSessions(@Param("now") LocalDateTime now);

    // ==================== Удаление ====================

    @Modifying
    void deleteByToken(String token);

    @Modifying
    void deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    // ==================== Статистика ====================

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.isActive = true")
    long countActiveSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.isActive = true")
    long countTotalActiveSessions();

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.expiresAt < :now AND s.isActive = true")
    long countExpiredActiveSessions(@Param("now") LocalDateTime now);

    // ==================== Поиск по FCM токену ====================

    @Query("SELECT s.userId FROM UserSession s WHERE s.fcmToken = :fcmToken AND s.isActive = true")
    Optional<Long> findUserIdByFcmToken(@Param("fcmToken") String fcmToken);

    @Query("SELECT s.fcmToken FROM UserSession s WHERE s.userId = :userId AND s.isActive = true AND s.fcmToken IS NOT NULL")
    List<String> findActiveFcmTokensByUserId(@Param("userId") Long userId);

    // ==================== Поиск по device ====================

    @Query("SELECT s FROM UserSession s WHERE s.deviceId = :deviceId AND s.isActive = true")
    List<UserSession> findActiveSessionsByDeviceId(@Param("deviceId") String deviceId);

    // ==================== Очистка старых сессий ====================

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.createdAt < :date AND s.isActive = false")
    int deleteInactiveSessionsOlderThan(@Param("date") LocalDateTime date);
}