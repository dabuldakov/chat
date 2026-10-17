package com.chat.server.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Связывает субъект единого IdP (auth-service, claim {@code sub}) с локальным
 * пользователем chat: находит по {@code auth_subject}, при отсутствии —
 * привязывает существующий аккаунт по email или создаёт новый.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcUserProvisioner {

    private static final int USERNAME_MAX = 50;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Намеренно без {@code @Transactional}: при гонке двух параллельных первых
     * запросов один падает с нарушением уникальности, и перечитать пользователя
     * нужно в НОВОЙ транзакции — иначе Hibernate-сессия уже помечена rollback-only
     * и повторное чтение роняет запрос (AssertionFailure: null identifier).
     * Транзакции открывают сами репозиторные методы.
     */
    public User findOrCreate(UUID subject, String username, String email) {
        Optional<User> existing = userRepository.findByAuthSubject(subject);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User user = byEmail.get();
                user.setAuthSubject(subject);
                if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                    user.setEmailVerified(true);
                }
                log.info("Linked auth subject {} to existing user {}", subject, user.getUserId());
                try {
                    return userRepository.save(user);
                } catch (DataIntegrityViolationException e) {
                    return userRepository.findByAuthSubject(subject).orElseThrow(() -> e);
                }
            }
        }

        return createUser(subject, username, email);
    }

    private User createUser(UUID subject, String username, String email) {
        User user = User.builder()
                .username(resolveUsername(username, email, subject))
                .email(resolveEmail(email, username, subject))
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(User.UserStatus.ACTIVE)
                .isOnline(false)
                .isDeleted(false)
                .emailVerified(true)
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

    private String resolveUsername(String username, String email, UUID subject) {
        String base = firstNonBlank(username, emailLocalPart(email), "user");
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

    private String resolveEmail(String email, String username, UUID subject) {
        if (email != null && !email.isBlank()) {
            return email;
        }
        String local = firstNonBlank(username, "user");
        return truncate(local, USERNAME_MAX) + "-" + subject.toString().substring(0, 8) + "@auth.local";
    }

    private String emailLocalPart(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
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
