package com.chat.server.integration;

import com.chat.server.notification.FcmService;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessageAccessors;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FcmServiceIT extends AbstractIntegrationTest {

    @Autowired
    private FcmService fcmService;

    @MockitoBean
    private FirebaseMessaging firebaseMessaging;

    @Test
    void shouldSendMessageNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendMessageNotification(
                "token-1", 10L, "Alice", "hello", null, null, null, null, null);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        Message sent = captor.getValue();
        assertThat(MessageAccessors.tokenOf(sent)).isEqualTo("token-1");
        assertThat(MessageAccessors.dataOf(sent).get("type")).isEqualTo("MESSAGE");
        assertThat(MessageAccessors.dataOf(sent).get("senderId")).isEqualTo("10");
        assertThat(MessageAccessors.dataOf(sent).get("senderName")).isEqualTo("Alice");
        // Заголовок уведомления — имя отправителя, а не «Новое сообщение».
        // FcmService кладёт данные без setNotification: уведомление строит клиент.
        assertThat(MessageAccessors.dataOf(sent).get("title")).isEqualTo("Alice");
    }

    @Test
    void shouldFallBackToGenericTitleWithoutSenderName() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendMessageNotification(
                "token-2", 11L, null, "hello", null, null, null, null, null);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        Message sent = captor.getValue();
        assertThat(MessageAccessors.dataOf(sent)).doesNotContainKey("senderName");
        assertThat(MessageAccessors.dataOf(sent).get("title")).isEqualTo("Новое сообщение");
    }

    @Test
    void shouldNotSendWithEmptyToken() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendMessageNotification(
                "", 1L, "Alice", "hello", null, null, null, null, null);

        verify(firebaseMessaging, after(500).never()).send(any());
    }

    @Test
    void shouldSendTypingNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendTypingNotification("token-1", 5L, 3L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("TYPING");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("chatId")).isEqualTo("5");
    }

    @Test
    void shouldSendCallNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendCallNotification("token-1", 5L, 3L, "VIDEO");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("CALL");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("callType")).isEqualTo("VIDEO");
    }

    @Test
    void shouldSendReadReceiptNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendReadReceiptNotification("token-1", 5L, 3L, 9L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("READ_RECEIPT");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("messageId")).isEqualTo("9");
    }

    @Test
    void shouldSendNewContactNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendNewContactNotification("token-1", "Alice", 9L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("NEW_CONTACT");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("contactName")).isEqualTo("Alice");
    }

    @Test
    void shouldSendGroupInviteNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendGroupInviteNotification("token-1", "Team", 5L, "Bob");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("GROUP_INVITE");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("groupName")).isEqualTo("Team");
    }

    @Test
    void shouldSendMessageDeletedNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendMessageDeletedNotification("token-1", 5L, 9L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        assertThat(MessageAccessors.dataOf(captor.getValue()).get("type")).isEqualTo("MESSAGE_DELETED");
        assertThat(MessageAccessors.dataOf(captor.getValue()).get("chatId")).isEqualTo("5");
    }

    @Test
    void shouldSendMulticastMessage() throws com.google.firebase.messaging.FirebaseMessagingException {
        BatchResponse response = mock(BatchResponse.class);
        when(response.getSuccessCount()).thenReturn(2);
        when(response.getFailureCount()).thenReturn(0);
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

        fcmService.sendMulticastMessage(List.of("t1", "t2"), "Title", "Body", Map.of("key", "value"));

        verify(firebaseMessaging, timeout(2000)).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void shouldNotSendMulticastWithEmptyTokens() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendMulticastMessage(List.of(), "Title", "Body", Map.of());

        verify(firebaseMessaging, after(500).never()).sendEachForMulticast(any());
    }

    @Test
    void shouldSendNewsNotification() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendNewsNotification("token-1", 42L, "Title", "body", "Alice");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        Map<String, String> data = MessageAccessors.dataOf(captor.getValue());
        assertThat(data.get("type")).isEqualTo("NEWS");
        assertThat(data.get("newsId")).isEqualTo("42");
        assertThat(data.get("title")).isEqualTo("Title");
        assertThat(data.get("body")).isEqualTo("body");
        assertThat(data.get("authorName")).isEqualTo("Alice");
    }

    @Test
    void newsNotificationTruncatesBodyAndOmitsNullAuthor() throws com.google.firebase.messaging.FirebaseMessagingException {
        fcmService.sendNewsNotification("token-1", 1L, "T", "x".repeat(200), null);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging, timeout(2000)).send(captor.capture());

        Map<String, String> data = MessageAccessors.dataOf(captor.getValue());
        assertThat(data.get("body")).hasSize(143).endsWith("...");
        assertThat(data).doesNotContainKey("authorName");
    }

    @Test
    void multicastHandlesFailedTokens() throws com.google.firebase.messaging.FirebaseMessagingException {
        BatchResponse response = mock(BatchResponse.class);
        SendResponse ok = mock(SendResponse.class);
        SendResponse failed = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        when(failed.isSuccessful()).thenReturn(false);
        when(response.getSuccessCount()).thenReturn(1);
        when(response.getFailureCount()).thenReturn(1);
        when(response.getResponses()).thenReturn(List.of(ok, failed));
        when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);

        fcmService.sendMulticastMessage(List.of("t1", "t2"), "Title", "Body", Map.of("key", "value"));

        verify(firebaseMessaging, timeout(2000)).sendEachForMulticast(any(MulticastMessage.class));
    }
}