package com.chat.server.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Связывает субъект единого IdP (auth-service, claim {@code sub}) с локальным
 * пользователем chat: находит по {@code auth_subject}, при отсутствии —
 * привязывает существующий аккаунт по username или создаёт новый.
 *
 * <p>Email здесь не хранится: это идентичность IdP, chat её не дублирует.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcUserProvisioner {

    private static final int USERNAME_MAX = 50;

    private final UserRepository userRepository;
    private final OidcProperties properties;

    /**
     * Создание сериализуем по субъекту: приложение на первом входе шлёт
     * несколько запросов сразу, и без этого они наперебой вставляют одного и
     * того же пользователя (часть получает нарушение уникальности). Блокировки
     * живут только на время операции.
     */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> provisionLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    public User findOrCreate(UUID subject, String username) {
        Object lock = provisionLocks.computeIfAbsent(subject, k -> new Object());
        synchronized (lock) {
            try {
                return doFindOrCreate(subject, username);
            } finally {
                provisionLocks.remove(subject, lock);
            }
        }
    }

    /**
     * Без общего {@code @Transactional}: если при гонке вставка падает по
     * уникальности, перечитать пользователя нужно в НОВОЙ транзакции, иначе
     * Hibernate-сессия уже rollback-only. Транзакции открывают репозиторные
     * методы.
     */
    private User doFindOrCreate(UUID subject, String username) {
        Optional<User> existing = userRepository.findByAuthSubject(subject);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Фолбэк миграции: пользователь вводит прежний логин (username) —
        // привязываем существующий аккаунт. Email для привязки не используется.
        if (properties.linkByUsername() && username != null && !username.isBlank()) {
            Optional<User> byUsername = userRepository.findByUsername(username);
            if (byUsername.isPresent()) {
                User user = byUsername.get();
                user.setAuthSubject(subject);
                log.info("Linked auth subject {} to existing user {} by username", subject, user.getUserId());
                try {
                    return userRepository.save(user);
                } catch (DataIntegrityViolationException e) {
                    return userRepository.findByAuthSubject(subject).orElseThrow(() -> e);
                }
            }
        }

        return createUser(subject, username);
    }

    private User createUser(UUID subject, String username) {
        User user = User.builder()
                .username(resolveUsername(username, subject))
                .status(User.UserStatus.ACTIVE)
                .isOnline(false)
                .isDeleted(false)
                .authSubject(subject)
                .build();
        try {
            User saved = userRepository.save(user);
            log.info("Provisioned chat user {} for auth subject {}", saved.getUserId(), subject);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Гонка: параллельный запрос уже создал пользователя с этим sub.
            return userRepository.findByAuthSubject(subject).orElseThrow(() -> e);
        }
    }

    private String resolveUsername(String username, UUID subject) {
        String base = firstNonBlank(username, "user");
        base = truncate(base, USERNAME_MAX);
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        String suffix = "_" + subject.toString().replace("-", "").substring(0, 6);
        String candidate = truncate(base, USERNAME_MAX - suffix.length()) + suffix;
        if (!userRepository.existsByUsername(candidate)) {
            return candidate;
        }
        return truncate(base, USERNAME_MAX - 9) + "_" + subject.toString().replace("-", "").substring(0, 8);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "user";
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
