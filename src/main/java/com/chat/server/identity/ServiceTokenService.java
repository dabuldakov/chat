package com.chat.server.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Выдача и проверка service-токенов.
 *
 * Токен имеет вид {@code mz_<32 random bytes base64url>}; в БД хранится
 * только его SHA-256. Токен привязан к обычному users.user_id, поэтому
 * права и участие в чатах работают как у пользователя-бота.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceTokenService {

    private static final String PREFIX = "mz_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServiceTokenRepository repository;

    /** Создаёт токен и возвращает его в открытом виде ЕДИНОЖДЫ. */
    @Transactional
    public String issue(Long userId, String name) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(ServiceToken.builder()
                .tokenHash(sha256(raw))
                .userId(userId)
                .name(name)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
        log.info("Issued service token '{}' for user {}", name, userId);
        return raw;
    }

    @Transactional
    public Optional<Long> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256(rawToken))
                .map(token -> {
                    token.setLastUsedAt(LocalDateTime.now(ZoneOffset.UTC));
                    repository.save(token);
                    return token.getUserId();
                });
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(sha256(rawToken))
                .ifPresent(repository::delete);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
