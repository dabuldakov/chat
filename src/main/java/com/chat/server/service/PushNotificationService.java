package com.chat.server.service;

import com.chat.server.entity.Message;
import com.chat.server.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserSessionRepository userSessionRepository;
    private final FcmService fcmService;

    /**
     * Уникальные FCM-токены активных сессий пользователя.
     * Один и тот же токен не должен получать уведомление дважды.
     */
    private List<String> distinctTokens(Long userId) {
        return userSessionRepository.findActiveFcmTokensByUserId(userId).stream()
                .filter(token -> token != null && !token.isEmpty())
                .distinct()
                .toList();
    }

    public void sendMessageNotification(Message message, List<Long> recipientIds) {
        // Отправляем уведомление всем участникам кроме отправителя
        List<Long> targetIds = recipientIds.stream()
                .filter(id -> !id.equals(message.getSenderId()))
                .toList();

        for (Long userId : targetIds) {
            for (String token : distinctTokens(userId)) {
                fcmService.sendMessageNotification(token, message.getSenderId(), message.getMessageText());
            }
        }
    }

    public void sendTypingNotification(Long chatId, Long userId, Long recipientId) {
        for (String token : distinctTokens(recipientId)) {
            fcmService.sendTypingNotification(token, chatId, userId);
        }
    }

    public void sendCallNotification(Long chatId, Long callerId, Long recipientId, String callType) {
        for (String token : distinctTokens(recipientId)) {
            fcmService.sendCallNotification(token, chatId, callerId, callType);
        }
    }

    public void sendReadReceipt(Long chatId, Long userId, Long messageId, List<Long> participantIds) {
        List<Long> targetIds = participantIds.stream()
                .filter(id -> !id.equals(userId))
                .toList();

        for (Long targetId : targetIds) {
            for (String token : distinctTokens(targetId)) {
                fcmService.sendReadReceiptNotification(token, chatId, userId, messageId);
            }
        }
    }

    public void sendNewContactNotification(Long userId, String contactName, Long contactId) {
        for (String token : distinctTokens(userId)) {
            fcmService.sendNewContactNotification(token, contactName, contactId);
        }
    }

    public void sendGroupInviteNotification(Long userId, String groupName, Long chatId, String inviterName) {
        for (String token : distinctTokens(userId)) {
            fcmService.sendGroupInviteNotification(token, groupName, chatId, inviterName);
        }
    }

    public void sendMulticastMessage(List<Long> userIds, String title, String body, Map<String, String> additionalData) {
        List<String> fcmTokens = userIds.stream()
                .flatMap(userId -> distinctTokens(userId).stream())
                .distinct()
                .toList();

        if (!fcmTokens.isEmpty()) {
            Map<String, String> data = new HashMap<>(additionalData);
            data.putIfAbsent("type", "BROADCAST");

            fcmService.sendMulticastMessage(fcmTokens, title, body, data);
        }
    }

    public void sendMessageDeletedNotification(Long chatId, Long messageId, List<Long> participantIds) {
        for (Long userId : participantIds) {
            for (String token : distinctTokens(userId)) {
                fcmService.sendMessageDeletedNotification(token, chatId, messageId);
            }
        }
    }
}
