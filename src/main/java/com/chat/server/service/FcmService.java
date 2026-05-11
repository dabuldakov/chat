package com.chat.server.service;

import com.google.firebase.messaging.*;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final Gson gson = new Gson();

    /**
     * Отправка уведомления о новом сообщении
     */
    public void sendMessageNotification(String fcmToken, Long senderId, String messageText) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            log.warn("Cannot send notification: FCM token is null or empty");
            return;
        }

        try {
            // Сокращаем текст сообщения для уведомления
            String shortText = messageText.length() > 100
                    ? messageText.substring(0, 100) + "..."
                    : messageText;

            // Создаем уведомление
            Notification notification = Notification.builder()
                    .setTitle("Новое сообщение")
                    .setBody(shortText)
                    .setImage(null)
                    .build();

            // Создаем данные для клиента
            Map<String, String> data = new HashMap<>();
            data.put("type", "MESSAGE");
            data.put("senderId", String.valueOf(senderId));
            data.put("messageText", messageText);

            // Создаем сообщение
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("chat_messages")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .setDefaultSound(true)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            // Отправляем
            String response = firebaseMessaging.send(message);
            log.debug("Message notification sent successfully: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send message notification to token: {}", fcmToken, e);
            handleError(fcmToken, e);
        }
    }

    /**
     * Отправка уведомления о том, что пользователь печатает
     */
    public void sendTypingNotification(String fcmToken, Long chatId, Long userId) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "TYPING");
            data.put("chatId", String.valueOf(chatId));
            data.put("userId", String.valueOf(userId));

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.NORMAL)
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Typing notification sent successfully: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send typing notification to token: {}", fcmToken, e);
        }
    }

    /**
     * Отправка уведомления о звонке
     */
    public void sendCallNotification(String fcmToken, Long chatId, Long callerId, String callType) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .setTitle("Входящий звонок")
                    .setBody(callType.equals("VIDEO") ? "Видеозвонок..." : "Аудиозвонок...")
                    .build();

            Map<String, String> data = new HashMap<>();
            data.put("type", "CALL");
            data.put("chatId", String.valueOf(chatId));
            data.put("callerId", String.valueOf(callerId));
            data.put("callType", callType);

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Call notification sent successfully: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send call notification to token: {}", fcmToken, e);
        }
    }

    /**
     * Отправка уведомления о прочтении сообщения
     */
    public void sendReadReceiptNotification(String fcmToken, Long chatId, Long userId, Long messageId) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "READ_RECEIPT");
            data.put("chatId", String.valueOf(chatId));
            data.put("userId", String.valueOf(userId));
            data.put("messageId", String.valueOf(messageId));

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.NORMAL)
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Read receipt sent successfully: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send read receipt to token: {}", fcmToken, e);
        }
    }

    /**
     * Отправка массовых уведомлений
     */
    public void sendMulticastMessage(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            log.warn("Cannot send multicast: FCM tokens list is empty");
            return;
        }

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(fcmTokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .build();

            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

            log.info("Multicast notification sent. Success: {}, Failure: {}",
                    response.getSuccessCount(), response.getFailureCount());

            // Обрабатываем неудачные отправки
            if (response.getFailureCount() > 0) {
                handleFailedTokens(fcmTokens, response);
            }

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send multicast notification", e);
        }
    }

    /**
     * Отправка уведомления о новом контакте
     */
    public void sendNewContactNotification(String fcmToken, String contactName, Long contactId) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .setTitle("Новый контакт")
                    .setBody(contactName + " добавил(а) вас в контакты")
                    .build();

            Map<String, String> data = new HashMap<>();
            data.put("type", "NEW_CONTACT");
            data.put("contactId", String.valueOf(contactId));
            data.put("contactName", contactName);

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putAllData(data)
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("New contact notification sent: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send new contact notification", e);
        }
    }

    /**
     * Отправка уведомления о добавлении в группу
     */
    public void sendGroupInviteNotification(String fcmToken, String groupName, Long chatId, String inviterName) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Notification notification = Notification.builder()
                    .setTitle("Приглашение в группу")
                    .setBody(inviterName + " пригласил(а) вас в группу \"" + groupName + "\"")
                    .build();

            Map<String, String> data = new HashMap<>();
            data.put("type", "GROUP_INVITE");
            data.put("chatId", String.valueOf(chatId));
            data.put("groupName", groupName);
            data.put("inviterName", inviterName);

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification)
                    .putAllData(data)
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Group invite notification sent: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send group invite notification", e);
        }
    }

    /**
     * Отправка уведомления об удалении сообщения
     */
    public void sendMessageDeletedNotification(String fcmToken, Long chatId, Long messageId) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            return;
        }

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "MESSAGE_DELETED");
            data.put("chatId", String.valueOf(chatId));
            data.put("messageId", String.valueOf(messageId));

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .build();

            String response = firebaseMessaging.send(message);
            log.debug("Message deleted notification sent: {}", response);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send message deleted notification", e);
        }
    }

    /**
     * Обработка ошибок отправки
     */
    private void handleError(String fcmToken, FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
            log.warn("FCM token is unregistered: {}", fcmToken);
            // Здесь можно удалить невалидный токен из базы
        } else if (e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
            log.warn("Invalid FCM token: {}", fcmToken);
        } else {
            log.error("FCM error: {}", e.getMessagingErrorCode());
        }
    }

    /**
     * Обработка неудачных отправок при массовой рассылке
     */
    private void handleFailedTokens(List<String> fcmTokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                String failedToken = fcmTokens.get(i);
                log.warn("Failed to send to token: {}", failedToken);
                // Здесь можно пометить токен как невалидный
            }
        }
    }
}