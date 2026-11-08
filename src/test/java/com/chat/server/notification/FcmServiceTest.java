package com.chat.server.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ветки FcmService, которые не проходит интеграционный тест: FCM выключен,
 * пустые токены, ошибки Firebase и частичные неудачи multicast.
 */
class FcmServiceTest {

    private ObjectProvider<FirebaseMessaging> provider;
    private FirebaseMessaging messaging;
    private FcmService service;

    @BeforeEach
    void setUp() {
        provider = mock(ObjectProvider.class);
        messaging = mock(FirebaseMessaging.class);
        service = new FcmService(provider);
    }

    private void callEveryMethod(String token) {
        UUID uuid = UUID.randomUUID();
        service.sendMessageNotification(token, 1L, "Alice", "hello", uuid, uuid, "Team", uuid, "avatar");
        service.sendNewsNotification(token, 1L, "Title", "Body", "Author");
        service.sendTypingNotification(token, 1L, 2L);
        service.sendCallNotification(token, 1L, 2L, "VIDEO");
        service.sendReadReceiptNotification(token, 1L, 2L, 3L);
        service.sendNewContactNotification(token, "Bob", 2L);
        service.sendGroupInviteNotification(token, "Team", 1L, "Bob");
        service.sendMessageDeletedNotification(token, 1L, 2L);
    }

    @Test
    void disabledFirebaseMakesEveryMethodANoOp() {
        when(provider.getIfAvailable()).thenReturn(null);

        callEveryMethod("token");
        service.sendMulticastMessage(List.of(), "T", "B", Map.of());

        verifyNoInteractions(messaging);
    }

    @Test
    void blankTokensAreIgnored() {
        when(provider.getIfAvailable()).thenReturn(messaging);

        callEveryMethod(null);
        callEveryMethod("");
        service.sendMulticastMessage(null, "Title", "Body", Map.of());
        service.sendMulticastMessage(List.of(), "Title", "Body", Map.of());

        verifyNoInteractions(messaging);
    }

    @Test
    void messageNotificationSendsAllOptionalFields() throws FirebaseMessagingException {
        when(provider.getIfAvailable()).thenReturn(messaging);
        String longText = "x".repeat(150);
        UUID chat = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        UUID sender = UUID.randomUUID();

        service.sendMessageNotification(
                "token", 1L, "Alice", longText, chat, message, "Team", sender, "avatar.png");

        verify(messaging).send(any(Message.class));
    }

    @Test
    void sendErrorsAreCaughtForEveryMethod() throws FirebaseMessagingException {
        when(provider.getIfAvailable()).thenReturn(messaging);
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(error);

        callEveryMethod("token");

        verify(messaging, org.mockito.Mockito.atLeastOnce()).send(any(Message.class));
    }

    @Test
    void multicastHandlesPartialFailures() throws FirebaseMessagingException {
        when(provider.getIfAvailable()).thenReturn(messaging);
        BatchResponse response = mock(BatchResponse.class);
        SendResponse ok = mock(SendResponse.class);
        SendResponse failed = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        when(failed.isSuccessful()).thenReturn(false);
        when(response.getResponses()).thenReturn(List.of(ok, failed, failed));
        when(messaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

        service.sendMulticastMessage(List.of("t1", "t2", "t3"), "Title", "Body", Map.of("k", "v"));

        verify(messaging).sendEachForMulticast(any(MulticastMessage.class));
        verify(messaging, never()).send(any(Message.class));
    }
}
