package com.chat.server.repository;

import com.chat.server.entity.MessageStatus;
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
public interface MessageStatusRepository extends JpaRepository<MessageStatus, Long> {

    // ==================== Поиск по UUID ====================

    Optional<MessageStatus> findByStatusUuid(UUID statusUuid);

    // ==================== Поиск по сообщению ====================

    List<MessageStatus> findByMessageId(Long messageId);

    @Query("SELECT ms FROM MessageStatus ms WHERE ms.messageId = :messageId AND ms.userId = :userId")
    Optional<MessageStatus> findByMessageIdAndUserId(@Param("messageId") Long messageId,
                                                     @Param("userId") Long userId);

    // ==================== Поиск непрочитанных сообщений ====================

    @Query("""
        SELECT ms FROM MessageStatus ms 
        WHERE ms.messageId IN (
            SELECT m.messageId FROM Message m 
            WHERE m.chatId = :chatId
        ) 
        AND ms.userId = :userId 
        AND ms.status != 'READ'
    """)
    List<MessageStatus> findUnreadMessagesInChat(@Param("chatId") Long chatId,
                                                 @Param("userId") Long userId);

    @Query(value = """
        SELECT ms.* FROM message_statuses ms
        JOIN messages m ON ms.message_id = m.message_id
        WHERE m.chat_id = :chatId 
        AND ms.user_id = :userId 
        AND ms.status != 'READ'
    """, nativeQuery = true)
    List<MessageStatus> findUnreadMessagesInChatNative(@Param("chatId") Long chatId,
                                                       @Param("userId") Long userId);

    // ==================== Обновление статусов ====================

    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.status = 'DELIVERED', ms.deliveredAt = :deliveredAt WHERE ms.messageId = :messageId AND ms.userId = :userId")
    void markAsDelivered(@Param("messageId") Long messageId,
                         @Param("userId") Long userId,
                         @Param("deliveredAt") LocalDateTime deliveredAt);

    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.status = 'READ', ms.readAt = :readAt, ms.deliveredAt = COALESCE(ms.deliveredAt, :readAt) WHERE ms.messageId = :messageId AND ms.userId = :userId")
    void markAsRead(@Param("messageId") Long messageId,
                    @Param("userId") Long userId,
                    @Param("readAt") LocalDateTime readAt);

    // ⭐ Массовое обновление сообщений как прочитанных (оптимизированная версия)
    @Modifying
    @Query("""
        UPDATE MessageStatus ms 
        SET ms.status = 'READ', ms.readAt = :readAt, ms.deliveredAt = COALESCE(ms.deliveredAt, :readAt)
        WHERE ms.userId = :userId 
        AND ms.messageId IN (
            SELECT m.messageId FROM Message m 
            WHERE m.chatId = :chatId AND m.messageId <= :upToMessageId
        )
        AND ms.status != 'READ'
    """)
    int markMessagesAsReadInChat(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("upToMessageId") Long upToMessageId,
            @Param("readAt") LocalDateTime readAt
    );

    // ⭐ Обновление last_read_message_id в Participant (через ParticipantRepository, не здесь!)
    // Этот метод не должен быть в MessageStatusRepository, так как он обновляет Participant

    @Modifying
    @Query("UPDATE MessageStatus ms SET ms.status = 'FAILED' WHERE ms.messageId = :messageId AND ms.userId = :userId")
    void markAsFailed(@Param("messageId") Long messageId,
                      @Param("userId") Long userId);

    // ==================== Массовое обновление ====================

    @Modifying
    @Query("""
        UPDATE MessageStatus ms 
        SET ms.status = 'READ', ms.readAt = :readAt 
        WHERE ms.messageId IN :messageIds AND ms.userId = :userId
    """)
    void markMultipleAsRead(@Param("messageIds") List<Long> messageIds,
                            @Param("userId") Long userId,
                            @Param("readAt") LocalDateTime readAt);

    // ==================== Удаление ====================

    @Modifying
    void deleteByMessageId(Long messageId);

    @Modifying
    @Query("DELETE FROM MessageStatus ms WHERE ms.messageId IN :messageIds")
    void deleteByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Modifying
    @Query("DELETE FROM MessageStatus ms WHERE ms.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // ==================== Проверки ====================

    @Query("SELECT CASE WHEN COUNT(ms) > 0 THEN true ELSE false END FROM MessageStatus ms WHERE ms.messageId = :messageId AND ms.userId = :userId AND ms.status = 'READ'")
    boolean isMessageReadByUser(@Param("messageId") Long messageId,
                                @Param("userId") Long userId);

    @Query("SELECT COUNT(ms) = (SELECT COUNT(DISTINCT p.userId) FROM Participant p WHERE p.chatId = :chatId) FROM MessageStatus ms WHERE ms.messageId = :messageId AND ms.status = 'READ'")
    boolean isMessageReadByAll(@Param("messageId") Long messageId, @Param("chatId") Long chatId);

    // ==================== Статистика ====================

    @Query("SELECT ms.status, COUNT(ms) FROM MessageStatus ms WHERE ms.messageId = :messageId GROUP BY ms.status")
    List<Object[]> getStatusDistribution(@Param("messageId") Long messageId);

    @Query("""
        SELECT AVG(EXTRACT(EPOCH FROM (ms.readAt - m.createdAt))) 
        FROM MessageStatus ms 
        JOIN Message m ON ms.messageId = m.messageId 
        WHERE m.chatId = :chatId AND ms.status = 'READ'
    """)
    Double getAverageReadTimeInChat(@Param("chatId") Long chatId);
}