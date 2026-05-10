package com.chat.server.service;

import com.chat.server.entity.Message;
import com.chat.server.entity.UserSession;
import com.chat.server.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserSessionRepository userSessionRepository;
    private final FcmService fcmService; // Firebase Cloud Messaging

    public void sendMessageNotification(Message message, List<Long> recipientIds) {
        // Отправляем уведомление всем участникам кроме отправителя
        List<Long> targetIds = recipientIds.stream()
                .filter(id -> !id.equals(message.getSenderId()))
                .toList();

        for (Long userId : targetIds) {
            List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(userId);

            for (UserSession session : sessions) {
                if (session.getFcmToken() != null) {
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
            if (session.getFcmToken() != null) {
                fcmService.sendTypingNotification(session.getFcmToken(), chatId, userId);
            }
        }
    }

    public void sendCallNotification(Long chatId, Long callerId, Long recipientId, String callType) {
        List<UserSession> sessions = userSessionRepository.findActiveSessionsByUserId(recipientId);

        for (UserSession session : sessions) {
            if (session.getFcmToken() != null) {
                fcmService.sendCallNotification(session.getFcmToken(), chatId, callerId, callType);
            }
        }
    }
}