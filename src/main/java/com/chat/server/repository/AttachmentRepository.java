package com.chat.server.repository;

import com.chat.server.entity.Attachment;
import org.springframework.data.domain.Pageable;
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

    // ==================== Поиск по UUID ====================

    Optional<Attachment> findByAttachmentUuid(UUID attachmentUuid);

    // ==================== Поиск по сообщению ====================

    @Query("SELECT a FROM Attachment a WHERE a.messageId = :messageId")
    List<Attachment> findByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT a FROM Attachment a WHERE a.messageId = :messageId AND a.isCompressed = false")
    List<Attachment> findOriginalsByMessageId(@Param("messageId") Long messageId);

    // ==================== Поиск по чату ====================

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

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId ORDER BY a.createdAt DESC")
    List<Attachment> findByChatId(@Param("chatId") Long chatId, Pageable pageable);

    // ==================== Поиск медиа файлов ====================

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId AND a.type IN ('IMAGE', 'VIDEO') ORDER BY a.createdAt DESC")
    List<Attachment> findMediaByChatId(@Param("chatId") Long chatId);

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

    // ==================== Поиск по типу ====================

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId AND a.type = :type ORDER BY a.createdAt DESC")
    List<Attachment> findByChatIdAndType(@Param("chatId") Long chatId,
                                         @Param("type") Attachment.AttachmentType type);

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId AND a.type IN :types ORDER BY a.createdAt DESC")
    List<Attachment> findByChatIdAndTypes(@Param("chatId") Long chatId,
                                          @Param("types") List<Attachment.AttachmentType> types);

    // ==================== Поиск по загрузившему ====================

    @Query("SELECT a FROM Attachment a WHERE a.uploaderId = :uploaderId ORDER BY a.createdAt DESC")
    List<Attachment> findByUploaderId(@Param("uploaderId") Long uploaderId);

    // ==================== Подсчет ====================

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.messageId = :messageId")
    long countByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.chatId = :chatId")
    long countByChatId(@Param("chatId") Long chatId);

    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.chatId = :chatId AND a.type IN ('IMAGE', 'VIDEO')")
    long countMediaByChatId(@Param("chatId") Long chatId);

    // ==================== Удаление ====================

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId IN :messageIds")
    void deleteByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.chatId = :chatId")
    void deleteByChatId(@Param("chatId") Long chatId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.uploaderId = :uploaderId")
    void deleteByUploaderId(@Param("uploaderId") Long uploaderId);

    // ==================== Обновление ====================

    @Modifying
    @Query("UPDATE Attachment a SET a.messageId = :messageId WHERE a.attachmentId = :attachmentId")
    void updateMessageId(@Param("attachmentId") Long attachmentId,
                         @Param("messageId") Long messageId);

    // ==================== Поиск по размеру ====================

    @Query("SELECT a FROM Attachment a WHERE a.fileSize > :minSize ORDER BY a.fileSize DESC")
    List<Attachment> findLargeAttachments(@Param("minSize") Long minSize, Pageable pageable);

    // ==================== Старые вложения (для очистки) ====================

    @Query("SELECT a FROM Attachment a WHERE a.messageId IS NULL AND a.createdAt < :date")
    List<Attachment> findOrphanedAttachments(@Param("date") java.time.LocalDateTime date);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.messageId IS NULL AND a.createdAt < :date")
    int deleteOrphanedAttachments(@Param("date") java.time.LocalDateTime date);

    // ==================== Статистика по чату ====================

    @Query("""
        SELECT a.type, COUNT(a), SUM(a.fileSize) 
        FROM Attachment a 
        WHERE a.chatId = :chatId 
        GROUP BY a.type
    """)
    List<Object[]> getAttachmentStatsByChat(@Param("chatId") Long chatId);

    // ==================== Поиск по расширению ====================

    @Query("SELECT a FROM Attachment a WHERE a.chatId = :chatId AND a.fileName LIKE CONCAT('%.', :extension)")
    List<Attachment> findByChatIdAndFileExtension(@Param("chatId") Long chatId,
                                                  @Param("extension") String extension);

    // ==================== Неполные вложения ====================

    @Query("SELECT a FROM Attachment a WHERE a.fileSize IS NULL OR a.fileUrl IS NULL")
    List<Attachment> findIncompleteAttachments();
}