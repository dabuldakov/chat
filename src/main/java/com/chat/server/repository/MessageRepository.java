package com.chat.server.repository;

import com.chat.server.entity.Message;
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

    // ==================== Поиск по UUID ====================

    Optional<Message> findByMessageUuid(UUID messageUuid);

    // ==================== Поиск сообщений в чате ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> findMessagesByChatId(@Param("chatId") Long chatId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesByChatId(@Param("chatId") Long chatId);

    @Query(value = """
        SELECT * FROM messages m
        WHERE m.chat_id = :chatId
        AND m.is_deleted = false
        ORDER BY m.created_at DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Message> findMessagesByChatIdPaginated(
            @Param("chatId") Long chatId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    // ==================== Поиск по времени ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.createdAt < :before AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesBefore(
            @Param("chatId") Long chatId,
            @Param("before") LocalDateTime before,
            @Param("limit") int limit
    );

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.createdAt > :after AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<Message> findMessagesAfter(
            @Param("chatId") Long chatId,
            @Param("after") LocalDateTime after
    );

    @Query(value = """
        SELECT m.* FROM messages m
        WHERE m.chat_id = :chatId
        AND m.created_at > :since
        AND m.is_deleted = false
        ORDER BY m.created_at ASC
    """, nativeQuery = true)
    List<Message> findNewMessagesSince(
            @Param("chatId") Long chatId,
            @Param("since") LocalDateTime since
    );

    // ==================== Подсчет сообщений ====================

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false")
    long countMessagesInChat(@Param("chatId") Long chatId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatId = :chatId AND m.messageId > :afterMessageId AND m.isDeleted = false")
    long countMessagesAfterId(
            @Param("chatId") Long chatId,
            @Param("afterMessageId") Long afterMessageId
    );

    // ==================== Поиск по ID сообщения ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.messageId < :beforeMessageId AND m.isDeleted = false ORDER BY m.messageId DESC")
    List<Message> findMessagesBeforeMessageId(
            @Param("chatId") Long chatId,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.messageId > :afterMessageId AND m.isDeleted = false ORDER BY m.messageId ASC")
    List<Message> findMessagesAfterMessageId(
            @Param("chatId") Long chatId,
            @Param("afterMessageId") Long afterMessageId,
            Pageable pageable
    );

    // ==================== Поиск ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND LOWER(m.messageText) LIKE LOWER(CONCAT('%', :keyword, '%')) AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> searchMessagesInChat(
            @Param("chatId") Long chatId,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    @Query("SELECT m FROM Message m WHERE m.chatId IN :chatIds AND LOWER(m.messageText) LIKE LOWER(CONCAT('%', :keyword, '%')) AND m.isDeleted = false ORDER BY m.createdAt DESC")
    Page<Message> searchMessagesInChats(
            @Param("chatIds") List<Long> chatIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // ==================== Закрепленные сообщения ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isPinned = true AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findPinnedMessages(@Param("chatId") Long chatId);

    @Modifying
    @Query("UPDATE Message m SET m.isPinned = :pinned WHERE m.messageId = :messageId")
    void updatePinStatus(@Param("messageId") Long messageId, @Param("pinned") boolean pinned);

    // ==================== Первое/последнее сообщение ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt ASC LIMIT 1")
    Optional<Message> findFirstMessageInChat(@Param("chatId") Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC LIMIT 1")
    Optional<Message> findLastMessageInChat(@Param("chatId") Long chatId);

    // ==================== Удаление ====================

    @Modifying
    @Query("UPDATE Message m SET m.isDeleted = true, m.deletedAt = :deletedAt, m.deletedBy = :deletedBy WHERE m.chatId = :chatId")
    void softDeleteAllMessagesInChat(
            @Param("chatId") Long chatId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedBy") Long deletedBy
    );

    @Modifying
    @Query("DELETE FROM Message m WHERE m.chatId = :chatId")
    void hardDeleteAllMessagesInChat(@Param("chatId") Long chatId);

    // ==================== Поиск по типу ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.messageType = :type AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesByType(
            @Param("chatId") Long chatId,
            @Param("type") Message.MessageType type,
            Pageable pageable
    );

    // ==================== Поиск по отправителю ====================

    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId AND m.senderId = :senderId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findMessagesBySender(
            @Param("chatId") Long chatId,
            @Param("senderId") Long senderId,
            Pageable pageable
    );

    // ==================== Поиск с ответами ====================

    @Query("SELECT m FROM Message m WHERE m.replyToMessageId = :messageId AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<Message> findRepliesToMessage(@Param("messageId") Long messageId);

    // ==================== Количество непрочитанных ====================

    @Query("""
        SELECT COUNT(m) FROM Message m 
        WHERE m.chatId = :chatId 
        AND m.messageId > :lastReadMessageId 
        AND m.senderId != :userId 
        AND m.isDeleted = false
    """)
    long countUnreadMessages(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );
}