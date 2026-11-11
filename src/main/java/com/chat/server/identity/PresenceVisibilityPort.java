package com.chat.server.identity;

import java.util.Collection;
import java.util.Set;

/**
 * Кто из пользователей может видеть присутствие зрителя.
 *
 * <p>Интерфейс живёт в identity, а реализация — в conversation: так identity
 * не зависит от чатов (правило архитектурных тестов), но может спросить,
 * есть ли общий чат.
 */
public interface PresenceVisibilityPort {

    /** Подмножество [userIds], с которыми у [viewerId] есть общий чат. */
    Set<Long> visibleUserIds(Long viewerId, Collection<Long> userIds);
}
