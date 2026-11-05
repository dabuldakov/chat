package com.chat.server.newspush;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Внутренний endpoint для других сервисов (makeup): прислать push о новости.
 *
 * <p>Закрыт общим секретом в заголовке, а не JWT пользователя: вызывать может
 * только наш бэкенд. Если секрет не настроен — endpoint отвечает 403.
 */
@RestController
@RequestMapping("/api/internal/push")
@RequiredArgsConstructor
public class InternalPushController {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    @Value("${push.internal-token:}")
    private String internalToken;

    private final NewsPushService newsPushService;

    @PostMapping("/news")
    public ResponseEntity<Void> news(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestBody NewsPushRequest request
    ) {
        // Сравнение за постоянное время: не даём угадывать секрет по таймингу.
        if (internalToken == null || internalToken.isBlank() || token == null
                || !MessageDigest.isEqual(
                        internalToken.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.authorUsername() == null || request.newsId() == null) {
            return ResponseEntity.badRequest().build();
        }
        newsPushService.sendNewsNotification(
                request.authorUsername(),
                request.newsId(),
                request.title(),
                request.body()
        );
        return ResponseEntity.ok().build();
    }

    public record NewsPushRequest(
            String authorUsername,
            Long newsId,
            String title,
            String body
    ) {
    }
}
