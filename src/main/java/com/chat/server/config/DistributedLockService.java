package com.chat.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Лидер-выборы для {@code @Scheduled}-задач: при нескольких инстансах каждую
 * задачу выполняет ровно один из них.
 *
 * <p>Использует транзакционный advisory lock PostgreSQL:
 * {@code pg_try_advisory_xact_lock} захватывается на время транзакции и
 * освобождается автоматически при её завершении. Это не требует отдельной
 * таблицы/зависимости и корректно работает с пулом соединений — блокировка
 * привязана к транзакции, а не к конкретному соединению.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Выполняет задачу, только если удалось взять блокировку. Возвращает
     * {@code true}, если задача выполнена (лок был взят), и {@code false}, если
     * блокировку держит другой инстанс.
     */
    @Transactional
    public boolean runIfLockAcquired(long lockKey, Runnable task) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lockKey);
        if (Boolean.TRUE.equals(acquired)) {
            task.run();
            return true;
        }
        log.debug("Scheduled task skipped: lock {} is held by another instance", lockKey);
        return false;
    }
}
