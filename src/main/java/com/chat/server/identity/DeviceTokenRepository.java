package com.chat.server.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByUserIdAndDeviceId(Long userId, String deviceId);

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    @Query("SELECT d.fcmToken FROM DeviceToken d WHERE d.userId = :userId AND d.fcmToken IS NOT NULL")
    List<String> findFcmTokensByUserId(@Param("userId") Long userId);

    @Query("SELECT d.fcmToken FROM DeviceToken d WHERE d.userId IN :userIds AND d.fcmToken IS NOT NULL")
    List<String> findFcmTokensByUserIds(@Param("userIds") List<Long> userIds);

    void deleteByUserId(Long userId);

    /** Смена пользователя на устройстве: чужой токен того же устройства не нужен. */
    void deleteByFcmTokenAndUserIdNot(String fcmToken, Long userId);
}
