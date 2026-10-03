package com.chat.server.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByMessageUuid(UUID messageUuid);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> findMessagesByChatId(@Param("chatId") Long chatId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesByChatId(@Param("chatId") Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.createdAt < :before AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesBefore(
            @Param("chatId") Long chatId,
            @Param("before") LocalDateTime before,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.createdAt > :after AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfter(
            @Param("chatId") Long chatId,
            @Param("after") LocalDateTime after,
            Pageable pageable
    );

    /**
     * Пакетная выгрузка сообщений после времени :since сразу для нескольких чатов
     * с ограничением :limit сообщений на каждый чат (ROW_NUMBER по партиции чата).
     * Заменяет цикл из N отдельных SELECT (N = число чатов пользователя).
     */
    @Query(value = """
        SELECT sub.* FROM (
            SELECT m.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY m.chat_id
                       ORDER BY m.created_at DESC
                   ) AS rn
            FROM messages m
            WHERE m.chat_id IN :chatIds
              AND m.created_at > :since
              AND m.is_deleted = false
        ) sub
        WHERE sub.rn <= :limit
        ORDER BY sub.created_at ASC
        """, nativeQuery = true)
    List<Message> findMessagesAfterForChats(
            @Param("chatIds") List<Long> chatIds,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false")
    long countMessagesInChat(@Param("chatId") Long chatId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId AND m.messageId > :afterMessageId AND m.isDeleted = false")
    long countMessagesAfterId(
            @Param("chatId") Long chatId,
            @Param("afterMessageId") Long afterMessageId
    );

    /**
     * Суммарное количество не удалённых сообщений по всем чатам за один запрос
     * (GROUP BY) — заменяет цикл из N запросов COUNT в SyncService.getSyncStatus.
     */
    @Query("SELECT m.chatId, COUNT(m) FROM Message m WHERE m.chatId IN :chatIds AND m.isDeleted = false GROUP BY m.chatId")
    List<Object[]> countMessagesInChats(@Param("chatIds") List<Long> chatIds);

    /**
     * Непрочитанные сообщения для пользователя сразу по всем его чатам (один запрос
     * вместо цикла из N × 2 SELECT в getUserChatsWithDetails/getTotalUnreadCount).
     * Учитывает per-chat last_read_message_id: если NULL — считаются все не удалённые
     * сообщения чата от других пользователей, иначе только с message_id > last_read_message_id.
     * Собственные сообщения пользователя никогда не считаются непрочитанными.
     * LEFT JOIN позволяет корректно вернуть 0 для чатов без сообщений.
     */
    @Query(value = """
        SELECT p.chat_id, COUNT(m.message_id)
        FROM participants p
        LEFT JOIN messages m
               ON m.chat_id = p.chat_id
              AND m.is_deleted = FALSE
              AND m.sender_id <> p.user_id
              AND (p.last_read_message_id IS NULL
                   OR m.message_id > p.last_read_message_id)
        WHERE p.user_id = :userId
          AND p.chat_id IN :chatIds
        GROUP BY p.chat_id
        """, nativeQuery = true)
    List<Object[]> countUnreadMessagesByChatIds(
            @Param("userId") Long userId,
            @Param("chatIds") List<Long> chatIds);

    /**
     * Полнотекстовый поиск сообщений в чате. Использует GIN-индекс
     * idx_messages_text_gin (V8) — без seq scan по партициям.
     */
    @Query(value = """
        SELECT m.* FROM messages m
        WHERE m.chat_id = :chatId
          AND m.is_deleted = false
          AND to_tsvector('russian', COALESCE(m.message_text, '')) @@
              (SELECT to_tsquery('russian',
                       string_agg(regexp_replace(trim(word), '[^\\w]', '', 'g') || ':*', ' & '))
               FROM regexp_split_to_table(:keyword, '\\s+') AS word)
        ORDER BY m.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Message> searchMessagesInChat(
            @Param("chatId") Long chatId,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    /**
     * Полнотекстовый поиск сообщений по нескольким чатам.
     */
    @Query(value = """
        SELECT m.* FROM messages m
        WHERE m.chat_id IN :chatIds
          AND m.is_deleted = false
          AND to_tsvector('russian', COALESCE(m.message_text, '')) @@
              (SELECT to_tsquery('russian',
                       string_agg(regexp_replace(trim(word), '[^\\w]', '', 'g') || ':*', ' & '))
               FROM regexp_split_to_table(:keyword, '\\s+') AS word)
        ORDER BY m.created_at DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM messages m
        WHERE m.chat_id IN :chatIds
          AND m.is_deleted = false
          AND to_tsvector('russian', COALESCE(m.message_text, '')) @@
              (SELECT to_tsquery('russian',
                       string_agg(regexp_replace(trim(word), '[^\\w]', '', 'g') || ':*', ' & '))
               FROM regexp_split_to_table(:keyword, '\\s+') AS word)
        """,
            nativeQuery = true)
    Page<Message> searchMessagesInChats(
            @Param("chatIds") List<Long> chatIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isPinned = true AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findPinnedMessages(@Param("chatId") Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findLastMessageInChat(@Param("chatId") Long chatId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.chatId = :chatId")
    void hardDeleteAllMessagesInChat(@Param("chatId") Long chatId);

    @Query("SELECT m.messageId FROM Message m WHERE m.chatId = :chatId")
    List<Long> findAllMessageIdsByChatId(@Param("chatId") Long chatId);

    /**
     * Полное удаление аккаунта: сообщения пользователя в чужих чатах. Каскад по
     * sender_id удалил бы их и так, но вызов нужен, чтобы заранее собрать
     * вложения — на messages у них нет FK, и каскад их не тронет.
     */
    @Query("SELECT m.messageId FROM Message m WHERE m.senderId = :senderId AND m.chatId IN :chatIds")
    List<Long> findAllMessageIdsBySenderIdAndChatIdIn(
            @Param("senderId") Long senderId,
            @Param("chatIds") List<Long> chatIds);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.senderId = :senderId AND m.chatId IN :chatIds")
    void hardDeleteBySenderIdAndChatIdIn(
            @Param("senderId") Long senderId,
            @Param("chatIds") List<Long> chatIds);

    /**
     * Сообщения в чате без учёта is_deleted — так же, как их считает
     * ChatRepository.updateLastMessage (message_count растёт при каждой
     * отправке).
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId")
    long countAllInChat(@Param("chatId") Long chatId);

    /**
     * Непрочитанные сообщения пользователя в чате на основе watermark-а
     * (participants.last_read_message_id). Если watermark NULL — непрочитанными
     * считаются все не удалённые сообщения от других пользователей.
     */
    @Query("""
        SELECT COUNT(m) FROM Message m
         WHERE m.chatId = :chatId
           AND m.senderId <> :userId
           AND m.isDeleted = false
           AND (:lastReadMessageId IS NULL OR m.messageId > :lastReadMessageId)
    """)
    long countUnreadMessagesForUser(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );
}
