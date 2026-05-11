package com.chat.server.service;

import com.chat.server.entity.Message;
import com.chat.server.entity.UserSession;
import com.chat.server.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserSessionRepository userSessionRepository;
    private final FcmService fcmService;

    public void sendMessageNotification(Message message, List<Long> recipientIds) {
        // Отправляем уведомление всем участникам кроме отправителя
        List<Long> targetIds = recipientIds.stream()
                .filter(id -> !id.equals(message.getSenderId()))
                .toList();

        for (Long userId : targetIds) {
            List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

            for (UserSession session : sessions) {
                if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                    fcmService.sendMessageNotification(
                            session.getFcmToken(),
                            message.getSenderId(),
                            message.getMessageText()
                    );
                }
            }
        }
    }

    public void sendTypingNotification(Long chatId, Long userId, Long recipientId) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(recipientId);

        for (UserSession session : sessions) {
            if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                fcmService.sendTypingNotification(session.getFcmToken(), chatId, userId);
            }
        }
    }

    public void sendCallNotification(Long chatId, Long callerId, Long recipientId, String callType) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(recipientId);

        for (UserSession session : sessions) {
            if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                fcmService.sendCallNotification(session.getFcmToken(), chatId, callerId, callType);
            }
        }
    }

    public void sendReadReceipt(Long chatId, Long userId, Long messageId, List<Long> participantIds) {
        List<Long> targetIds = participantIds.stream()
                .filter(id -> !id.equals(userId))
                .toList();

        for (Long targetId : targetIds) {
            List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(targetId);

            for (UserSession session : sessions) {
                if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                    fcmService.sendReadReceiptNotification(session.getFcmToken(), chatId, userId, messageId);
                }
            }
        }
    }

    public void sendNewContactNotification(Long userId, String contactName, Long contactId) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

        for (UserSession session : sessions) {
            if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                fcmService.sendNewContactNotification(session.getFcmToken(), contactName, contactId);
            }
        }
    }

    public void sendGroupInviteNotification(Long userId, String groupName, Long chatId, String inviterName) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

        for (UserSession session : sessions) {
            if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                fcmService.sendGroupInviteNotification(session.getFcmToken(), groupName, chatId, inviterName);
            }
        }
    }

    public void sendMulticastMessage(List<Long> userIds, String title, String body, Map<String, String> additionalData) {
        // Собираем все FCM токены пользователей
        List<String> fcmTokens = userIds.stream()
                .flatMap(userId -> userSessionRepository.findActiveSessionsByUserId(userId).stream())
                .filter(session -> session.getFcmToken() != null && !session.getFcmToken().isEmpty())
                .map(UserSession::getFcmToken)
                .distinct()
                .collect(Collectors.toList());

        if (!fcmTokens.isEmpty()) {
            Map<String, String> data = new HashMap<>(additionalData);
            data.putIfAbsent("type", "BROADCAST");

            fcmService.sendMulticastMessage(fcmTokens, title, body, data);
        }
    }

    public void sendMessageDeletedNotification(Long chatId, Long messageId, List<Long> participantIds) {
        for (Long userId : participantIds) {
            List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

            for (UserSession session : sessions) {
                if (session.getFcmToken() != null && !session.getFcmToken().isEmpty()) {
                    fcmService.sendMessageDeletedNotification(session.getFcmToken(), chatId, messageId);
                }
            }
        }
    }
}