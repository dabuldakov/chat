package com.chat.server.identity;

import com.chat.server.config.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Снимает флаг {@code is_online} у пользователей, чей heartbeat протух.
 *
 * <p>На ответы клиенту это не влияет — PresenceService считает «онлайн» из
 * {@code last_seen_at}. Свипер нужен, чтобы денормализованный флаг не расходился
 * с действительностью: на нём держатся индексы и выборки вроде
 * {@code findOnlineUsers()}. Без него флаг «залипнет» на ушедших без разлогина.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceSweeper {

    /** Ключ advisory-лока: при нескольких инстансах свипит только один. */
    private static final long LOCK_KEY = 1_845_001L;

    private final UserRepository userRepository;
    private final PresenceProperties properties;
    private final DistributedLockService lockService;

    @Scheduled(fixedDelayString = "${app.presence.sweep-interval-ms:60000}")
    public void expireStaleUsers() {
        lockService.runIfLockAcquired(LOCK_KEY, this::sweep);
    }

    private void sweep() {
        try {
            LocalDateTime threshold = LocalDateTime.now(ZoneOffset.UTC).minus(properties.onlineTtl());
            int expired = userRepository.expireStalePresence(threshold);
            if (expired > 0) {
                log.info("Presence sweep: marked {} user(s) offline", expired);
            }
        } catch (Exception e) {
            // Свипер — фоновая задача: падение не должно валить расписание.
            log.error("Presence sweep failed", e);
        }
    }
}
