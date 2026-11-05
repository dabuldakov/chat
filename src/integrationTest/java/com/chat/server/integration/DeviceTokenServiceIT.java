package com.chat.server.integration;

import com.chat.server.exception.BadRequestException;
import com.chat.server.identity.DeviceToken;
import com.chat.server.identity.DeviceTokenRepository;
import com.chat.server.identity.DeviceTokenService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Хранилище FCM-токенов устройств, заменившее парольные сессии chat.
 * Проверяем идемпотентность регистрации и перепривязку токена к другому
 * пользователю: пуш не должен уйти бывшему владельцу устройства.
 */
class DeviceTokenServiceIT extends AbstractIntegrationTest {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private DeviceTokenService deviceTokenService;
    @Autowired
    private UserRepository userRepository;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("dev-user")
                .build());
        other = userRepository.save(User.builder()
                .username("dev-other")
                .build());
    }

    @Test
    void registersTokenForUserAndDevice() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");

        assertThat(deviceTokenService.getFcmTokens(user.getUserId())).containsExactly("fcm-1");
        assertThat(deviceTokenRepository.findByUserIdAndDeviceId(user.getUserId(), "device-1"))
                .get()
                .extracting(DeviceToken::getFcmToken)
                .isEqualTo("fcm-1");
    }

    @Test
    void reRegisteringSameTokenDoesNotCreateDuplicate() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");

        assertThat(deviceTokenRepository.findFcmTokensByUserId(user.getUserId())).containsExactly("fcm-1");
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void sameDeviceWithNewTokenReplacesOldOne() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-old");
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-new");

        assertThat(deviceTokenService.getFcmTokens(user.getUserId())).containsExactly("fcm-new");
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void sameTokenOnAnotherUserReassignsIt() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "shared-fcm");
        deviceTokenService.registerFcmToken(other.getUserId(), "device-1", "shared-fcm");

        // Токен устройства принадлежит последнему вошедшему пользователю.
        assertThat(deviceTokenService.getFcmTokens(user.getUserId())).isEmpty();
        assertThat(deviceTokenService.getFcmTokens(other.getUserId())).containsExactly("shared-fcm");
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void blankTokenIsRejected() {
        assertThatThrownBy(() -> deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "  "))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> deviceTokenService.registerFcmToken(user.getUserId(), "device-1", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void nullDeviceIdIsStoredAsNullAndUpdatesIdempotently() {
        deviceTokenService.registerFcmToken(user.getUserId(), null, "fcm-1");
        assertThat(deviceTokenRepository.findByFcmToken("fcm-1"))
                .get()
                .extracting(DeviceToken::getDeviceId)
                .isNull();

        deviceTokenService.registerFcmToken(user.getUserId(), null, "fcm-1");
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void unregistersTokenAndIgnoresBlank() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");

        deviceTokenService.unregisterFcmToken("fcm-1");
        assertThat(deviceTokenRepository.findByFcmToken("fcm-1")).isEmpty();

        // Пустой токен — не ошибка, просто нечего удалять.
        deviceTokenService.unregisterFcmToken(null);
        deviceTokenService.unregisterFcmToken("  ");
    }

    @Test
    void deletesAllTokensOfUserOnly() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");
        deviceTokenService.registerFcmToken(user.getUserId(), "device-2", "fcm-2");
        deviceTokenService.registerFcmToken(other.getUserId(), "device-3", "fcm-3");

        deviceTokenService.deleteByUserId(user.getUserId());

        assertThat(deviceTokenService.getFcmTokens(user.getUserId())).isEmpty();
        assertThat(deviceTokenService.getFcmTokens(other.getUserId())).containsExactly("fcm-3");
    }

    @Test
    void findsTokensForMultipleUsers() {
        deviceTokenService.registerFcmToken(user.getUserId(), "device-1", "fcm-1");
        deviceTokenService.registerFcmToken(other.getUserId(), "device-2", "fcm-2");

        assertThat(deviceTokenRepository.findFcmTokensByUserIds(List.of(user.getUserId(), other.getUserId())))
                .containsExactlyInAnyOrder("fcm-1", "fcm-2");
    }
}
