package com.chat.server.identity;

import com.chat.server.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Хранение FCM-токенов устройств. Заменило парольные сессии chat (user_sessions):
 * вход теперь только через единый IdP, а push-токен нужен постоянно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Регистрирует или обновляет FCM-токен устройства. Идемпотентно: одно и то же
     * устройство не плодит дубли, а при смене пользователя на устройстве прежняя
     * привязка токена удаляется, чтобы пуш не ушёл бывшему владельцу.
     */
    @Transactional
    public void registerFcmToken(Long userId, String deviceId, String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new BadRequestException("FCM token is required");
        }
        // Тот же токен = то же приложение на устройстве: если раньше он был
        // привязан к другому пользователю — отвязываем.
        deviceTokenRepository.deleteByFcmTokenAndUserIdNot(fcmToken, userId);

        String normalizedDeviceId = (deviceId == null || deviceId.isBlank()) ? null : deviceId;

        DeviceToken existing = deviceTokenRepository.findByFcmToken(fcmToken)
                .or(() -> normalizedDeviceId == null
                        ? java.util.Optional.empty()
                        : deviceTokenRepository.findByUserIdAndDeviceId(userId, normalizedDeviceId))
                .orElse(null);

        if (existing == null) {
            deviceTokenRepository.save(DeviceToken.builder()
                    .userId(userId)
                    .deviceId(normalizedDeviceId)
                    .fcmToken(fcmToken)
                    .build());
        } else {
            existing.setUserId(userId);
            existing.setDeviceId(normalizedDeviceId);
            existing.setFcmToken(fcmToken);
            deviceTokenRepository.save(existing);
        }
        log.debug("FCM token registered for user: {}", userId);
    }

    @Transactional
    public void unregisterFcmToken(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        deviceTokenRepository.findByFcmToken(fcmToken)
                .ifPresent(deviceTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<String> getFcmTokens(Long userId) {
        return deviceTokenRepository.findFcmTokensByUserId(userId);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        deviceTokenRepository.deleteByUserId(userId);
    }
}
