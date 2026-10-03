package com.chat.server.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByAttachmentUuid(UUID attachmentUuid);

    @Query("SELECT a FROM Attachment a WHERE a.messageId = :messageId")
    List<Attachment> findByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId ORDER BY a.createdAt DESC")
    List<Attachment> findByChatId(@Param("chatId") Long chatId);

    @Query(value = """
        SELECT * FROM attachments a
        WHERE a.chat_id = :chatId
        ORDER BY a.created_at DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Attachment> findByChatId(@Param("chatId") Long chatId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    @Query(value = """
        SELECT * FROM attachments a
        WHERE a.chat_id = :chatId 
        AND a.type IN ('IMAGE', 'VIDEO')
        ORDER BY a.created_at DESC
        LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
    List<Attachment> findMediaByChatId(@Param("chatId") Long chatId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.messageId = :messageId")
    long countByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.chatId = :chatId")
    long countByChatId(@Param("chatId") Long chatId);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.chatId = :chatId AND a.type IN ('IMAGE', 'VIDEO')")
    long countMediaByChatId(@Param("chatId") Long chatId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId IN :messageIds")
    void deleteByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.chatId = :chatId")
    void deleteByChatId(@Param("chatId") Long chatId);

    /**
     * Файлы, загруженные пользователем, но не прикреплённые ни к одному
     * сообщению. Их не удалит ни каскад по пользователю, ни каскад по чату.
     */
    List<Attachment> findByUploaderIdAndMessageIdIsNull(Long uploaderId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.uploaderId = :uploaderId AND a.messageId IS NULL")
    void deleteByUploaderIdAndMessageIdIsNull(@Param("uploaderId") Long uploaderId);

    /**
     * Сироты: вложения без сообщения старше даты. Очищаются планировщиком.
     */
    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId IS NULL AND a.createdAt < :date")
    int deleteOrphanedAttachments(@Param("date") java.time.LocalDateTime date);
}
