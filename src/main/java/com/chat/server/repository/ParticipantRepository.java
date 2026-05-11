package com.chat.server.repository;

import com.chat.server.entity.Participant;
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
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    boolean existsByChatIdAndUserId(Long chatId, Long userId);

    Optional<Participant> findByChatIdAndUserId(Long chatId, Long userId);

    List<Participant> findAllByUserId(Long userId);

    List<Participant> findAllByChatId(Long chatId);

    @Query("SELECT p.userUUID FROM Participant p WHERE p.chatId = :chatId")
    List<UUID> findUserIdsByChatId(@Param("chatId") Long chatId);

    void deleteByChatIdAndUserId(Long chatId, Long userId);

    @Query("SELECT p.chatId FROM Participant p WHERE p.userId = :userId")
    List<Long> findChatIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT count(p.participantId) FROM Participant p WHERE p.chatId = :chatId")
    Long countByChatId(@Param("chatId") Long chatId);

    // ⭐ Обновление last_read_message_id
    @Modifying
    @Query("UPDATE Participant p SET p.lastReadMessageId = :messageId, p.lastReadAt = :readAt WHERE p.chatId = :chatId AND p.userId = :userId")
    void updateLastReadMessage(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("readAt") LocalDateTime readAt
    );

    // Альтернативный метод через Native Query
    @Modifying
    @Query(value = """
        UPDATE participants 
        SET last_read_message_id = :messageId, last_read_at = :readAt 
        WHERE chat_id = :chatId AND user_id = :userId
    """, nativeQuery = true)
    void updateLastReadMessageNative(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("readAt") LocalDateTime readAt
    );
}