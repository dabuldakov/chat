package com.chat.server.integration;

import com.chat.server.entity.Message;
import com.chat.server.entity.User;
import com.chat.server.entity.UserSession;
import com.chat.server.repository.UserRepository;
import com.chat.server.service.FcmService;
import com.chat.server.service.PushNotificationService;
import com.chat.server.service.UserSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PushNotificationServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSessionService userSessionService;
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
                .passwordHash("hash")
                .isOnline(false)
                .isDeleted(false)
                .build());
    }

    private UserSession sessionWithFcm(Long userId, String token, String fcmToken) {
        UserSession session = userSessionService.createSession(userId, token, "refresh-" + token, "device-" + token,
                "Pixel", "ANDROID", null, null);
        if (fcmToken != null) {
            userSessionService.updateFcmToken(session.getSessionId(), fcmToken);
        }
        return session;
    }

    private Message message(Long senderId, String text) {
        return Message.builder()
                .senderId(senderId)
                .messageText(text)
                .build();
    }

    @Test
    void shouldNotifyAllRecipientsExceptSender() {
        UserSession senderSession = sessionWithFcm(sender.getUserId(), "sender-tok", "fcm-sender");
        UserSession recipientSession = sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendMessageNotification(message(sender.getUserId(), "hi"), 
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendMessageNotification("fcm-recipient", sender.getUserId(), "hi");
        verify(fcmService, never()).sendMessageNotification("fcm-sender", sender.getUserId(), "hi");
    }

    @Test
    void shouldNotSendNotificationWithoutFcmToken() {
        sessionWithFcm(recipient.getUserId(), "recipient-tok", null);

        pushNotificationService.sendMessageNotification(message(sender.getUserId(), "hi"),
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService, never()).sendMessageNotification(any(), any(), any());
    }

    @Test
    void shouldSendTypingNotification() {
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendTypingNotification(5L, sender.getUserId(), recipient.getUserId());

        verify(fcmService).sendTypingNotification("fcm-recipient", 5L, sender.getUserId());
    }

    @Test
    void shouldSendCallNotification() {
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendCallNotification(5L, sender.getUserId(), recipient.getUserId(), "AUDIO");

        verify(fcmService).sendCallNotification("fcm-recipient", 5L, sender.getUserId(), "AUDIO");
    }

    @Test
    void shouldSendReadReceipt() {
        sessionWithFcm(sender.getUserId(), "sender-tok", "fcm-sender");

        pushNotificationService.sendReadReceipt(5L, recipient.getUserId(), 9L,
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendReadReceiptNotification("fcm-sender", 5L, recipient.getUserId(), 9L);
    }

    @Test
    void shouldSendNewContactNotification() {
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendNewContactNotification(recipient.getUserId(), "Alice", 9L);

        verify(fcmService).sendNewContactNotification("fcm-recipient", "Alice", 9L);
    }

    @Test
    void shouldSendGroupInviteNotification() {
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendGroupInviteNotification(recipient.getUserId(), "Team", 5L, "Bob");

        verify(fcmService).sendGroupInviteNotification("fcm-recipient", "Team", 5L, "Bob");
    }

    @Test
    void shouldSendMessageDeletedNotification() {
        sessionWithFcm(sender.getUserId(), "sender-tok", "fcm-sender");
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "fcm-recipient");

        pushNotificationService.sendMessageDeletedNotification(5L, 9L,
                List.of(sender.getUserId(), recipient.getUserId()));

        verify(fcmService).sendMessageDeletedNotification("fcm-sender", 5L, 9L);
        verify(fcmService).sendMessageDeletedNotification("fcm-recipient", 5L, 9L);
    }

    @Test
    void shouldSendMulticastWithDistinctTokens() {
        sessionWithFcm(sender.getUserId(), "sender-tok", "same-fcm");
        sessionWithFcm(recipient.getUserId(), "recipient-tok", "same-fcm");

        pushNotificationService.sendMulticastMessage(
                List.of(sender.getUserId(), recipient.getUserId()), "Title", "Body",
                Map.of("key", "value"));

        verify(fcmService).sendMulticastMessage(
                Mockito.eq(List.of("same-fcm")), Mockito.eq("Title"), Mockito.eq("Body"),
                Mockito.any(Map.class));
    }
}