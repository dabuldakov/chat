package com.chat.server.newspush;

import com.chat.server.contacts.ContactRepository;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import com.chat.server.identity.UserSessionRepository;
import com.chat.server.notification.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Push о новостях. Вынесен из notification-модуля, потому что для выбора
 * получателей нужны контакты: новость уходит только тем, у кого автор есть
 * в контактах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsPushService {

    private final UserSessionRepository userSessionRepository;
    private final UserService userService;
    private final ContactRepository contactRepository;
    private final FcmService fcmService;

    public void sendNewsNotification(String authorUsername, Long newsId, String title, String body) {
        User author;
        try {
            author = userService.findByUsername(authorUsername);
        } catch (Exception e) {
            log.warn("Cannot send news push: author not found: {}", authorUsername);
            return;
        }

        List<Long> recipientIds = contactRepository
                .findUserIdsByContactUserId(author.getUserId())
                .stream()
                .filter(id -> !id.equals(author.getUserId()))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            log.info("News push skipped: no contacts for author {}", authorUsername);
            return;
        }

        List<String> tokens = userSessionRepository.findActiveFcmTokensByUserIds(recipientIds).stream()
                .filter(token -> token != null && !token.isEmpty())
                .distinct()
                .toList();

        String authorName = author.getFullName() != null && !author.getFullName().isBlank()
                ? author.getFullName()
                : author.getUsername();
        for (String token : tokens) {
            fcmService.sendNewsNotification(token, newsId, title, body, authorName);
        }
    }
}
