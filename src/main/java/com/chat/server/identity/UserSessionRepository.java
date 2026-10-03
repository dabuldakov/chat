package com.chat.server.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByToken(String token);

    Optional<UserSession> findByRefreshToken(String refreshToken);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.isActive = true ORDER BY s.lastActivity DESC")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.deviceId = :deviceId AND s.isActive = true")
    Optional<UserSession> findActiveSessionByDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivity = :lastActivity WHERE s.token = :token")
    void updateLastActivity(@Param("token") String token, @Param("lastActivity") LocalDateTime lastActivity);

    @Modifying
    @Query("UPDATE UserSession s SET s.fcmToken = :fcmToken WHERE s.sessionId = :sessionId")
    void updateFcmToken(@Param("sessionId") Long sessionId, @Param("fcmToken") String fcmToken);

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

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredSessions(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.isActive = true")
    long countActiveSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT s.fcmToken FROM UserSession s WHERE s.userId = :userId AND s.isActive = true AND s.fcmToken IS NOT NULL")
    List<String> findActiveFcmTokensByUserId(@Param("userId") Long userId);

    @Query("SELECT s.fcmToken FROM UserSession s WHERE s.userId IN :userIds AND s.isActive = true AND s.fcmToken IS NOT NULL")
    List<String> findActiveFcmTokensByUserIds(@Param("userIds") List<Long> userIds);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.createdAt < :date AND s.isActive = false")
    int deleteInactiveSessionsOlderThan(@Param("date") LocalDateTime date);
}
