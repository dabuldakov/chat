package com.chat.server.integration;

import com.chat.server.identity.DeviceTokenRepository;
import com.chat.server.identity.DeviceTokenService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserRepository;
import com.chat.server.notification.FcmService;
import com.chat.server.notification.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PushNotificationServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeviceTokenService deviceTokenService;
    @MockitoSpyBean
    private DeviceTokenRepository deviceTokenRepository;
    @Autowired
    private PushNotificationService pushNotificationService;

    @MockitoBean
    private FcmService fcmService;

    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        sender = createUser("sender");
        recipient = createUser("recipient");
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    /**
     * Токены хранит таблица device_tokens: одна строка на устройство.
     */
    private void deviceWithFcm(Long userId, String deviceId, String fcmToken) {
        deviceTokenService.registerFcmToken(userId, deviceId, fcmToken);
    }

    private void sendMessage(User from, String text, List<Long> recipients) {
        pushNotificationService.sendMessageNotification(
                from.getUserId(), "Alice", text, recipients,
                UUID.randomUUID(), UUID.randomUUID(), "chat-title",
                from.getUserUuid(), "avatar-url");
    }

    @Test
    void shouldNotifyAllRecipientsExceptSender() {
        deviceWithFcm(sender.getUserId(), "device-sender", "fcm-sender");
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        sendMessage(sender, "hi", List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendMessageNotification(
                Mockito.eq("fcm-recipient"), Mockito.eq(sender.getUserId()), Mockito.eq("Alice"),
                Mockito.eq("hi"), any(), any(), any(), any(), any());
        verify(fcmService, never()).sendMessageNotification(
                Mockito.eq("fcm-sender"), anyLong(), anyString(), anyString(),
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotSendNotificationWithoutFcmToken() {
        // У получателя вообще нет зарегистрированного устройства.
        sendMessage(sender, "hi", List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService, never()).sendMessageNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotSendDuplicateNotificationForSameToken() {
        // В БД токен уникален (uk_device_tokens_fcm_token), но защитный distinct
        // в сервисе всё равно проверяем: подсовываем дубль на уровне запроса.
        Mockito.doReturn(List.of("fcm-same", "fcm-same"))
                .when(deviceTokenRepository).findFcmTokensByUserIds(List.of(recipient.getUserId()));

        sendMessage(sender, "hi", List.of(recipient.getUserId()));

        verify(fcmService, Mockito.times(1)).sendMessageNotification(
                Mockito.eq("fcm-same"), anyLong(), anyString(), anyString(),
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldSendTypingNotification() {
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        pushNotificationService.sendTypingNotification(5L, sender.getUserId(), recipient.getUserId());

        verify(fcmService).sendTypingNotification("fcm-recipient", 5L, sender.getUserId());
    }

    @Test
    void shouldSendCallNotification() {
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        pushNotificationService.sendCallNotification(5L, sender.getUserId(), recipient.getUserId(), "AUDIO");

        verify(fcmService).sendCallNotification("fcm-recipient", 5L, sender.getUserId(), "AUDIO");
    }

    @Test
    void shouldSendReadReceipt() {
        deviceWithFcm(sender.getUserId(), "device-sender", "fcm-sender");

        pushNotificationService.sendReadReceipt(5L, recipient.getUserId(), 9L,
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendReadReceiptNotification("fcm-sender", 5L, recipient.getUserId(), 9L);
    }

    @Test
    void shouldSendNewContactNotification() {
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        pushNotificationService.sendNewContactNotification(recipient.getUserId(), "Alice", 9L);

        verify(fcmService).sendNewContactNotification("fcm-recipient", "Alice", 9L);
    }

    @Test
    void shouldSendGroupInviteNotification() {
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        pushNotificationService.sendGroupInviteNotification(recipient.getUserId(), "Team", 5L, "Bob");

        verify(fcmService).sendGroupInviteNotification("fcm-recipient", "Team", 5L, "Bob");
    }

    @Test
    void shouldSendMessageDeletedNotification() {
        deviceWithFcm(sender.getUserId(), "device-sender", "fcm-sender");
        deviceWithFcm(recipient.getUserId(), "device-recipient", "fcm-recipient");

        pushNotificationService.sendMessageDeletedNotification(5L, 9L,
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendMessageDeletedNotification("fcm-sender", 5L, 9L);
        verify(fcmService).sendMessageDeletedNotification("fcm-recipient", 5L, 9L);
    }

    @Test
    void shouldSendMulticastWithDistinctTokens() {
        // Оба пользователя «указывают» на один и тот же токен: multicast должен
        // схлопнуть его в один. В БД такое состояние невозможно (токен уникален),
        // поэтому подменяем выдачу токенов.
        Mockito.doReturn(List.of("same-fcm"))
                .when(deviceTokenRepository).findFcmTokensByUserId(sender.getUserId());
        Mockito.doReturn(List.of("same-fcm"))
                .when(deviceTokenRepository).findFcmTokensByUserId(recipient.getUserId());

        pushNotificationService.sendMulticastMessage(
                List.of(sender.getUserId(), recipient.getUserId()), "Title", "Body",
                Map.of("key", "value"));

        verify(fcmService).sendMulticastMessage(
                Mockito.eq(List.of("same-fcm")), Mockito.eq("Title"), Mockito.eq("Body"),
                Mockito.any(Map.class));
    }
}
