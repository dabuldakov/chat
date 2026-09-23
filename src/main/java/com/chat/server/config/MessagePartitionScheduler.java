package com.chat.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Поддерживает месячные RANGE-партиции таблицы messages.
 *
 * Партиционирование по created_at не даёт «вечной истории» удалять старые
 * данные, но требует, чтобы новые партиции появлялись заранее — иначе вставка
 * уйдёт в messages_default (или упадёт без неё). Функция ensure_message_partitions
 * идемпотентна и создаёт партиции на окно вперёд.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePartitionScheduler {

    private static final int MONTHS_AHEAD = 6;

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        ensurePartitions();
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void ensurePartitionsDaily() {
        ensurePartitions();
    }

    private void ensurePartitions() {
        try {
            jdbcTemplate.execute("SELECT ensure_message_partitions(" + MONTHS_AHEAD + ")");
        } catch (Exception e) {
            log.error("Failed to ensure message partitions", e);
        }
    }
}
