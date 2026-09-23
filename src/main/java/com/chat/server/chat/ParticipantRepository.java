package com.chat.server.chat;

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
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    boolean existsByChatIdAndUserId(Long chatId, Long userId);

    Optional<Participant> findByChatIdAndUserId(Long chatId, Long userId);

    List<Participant> findAllByUserId(Long userId);

    List<Participant> findAllByChatId(Long chatId);

    // Пакетная выборка участников сразу по всем чатам пользователя
    // (1 запрос вместо N отдельных findAllByChatId в списке чатов).
    List<Participant> findAllByChatIdIn(List<Long> chatIds);

    @Query("SELECT p.userUUID FROM Participant p WHERE p.chatId = :chatId")
    List<UUID> findUserIdsByChatId(@Param("chatId") Long chatId);

    void deleteByChatIdAndUserId(Long chatId, Long userId);

    @Query("SELECT p.chatId FROM Participant p WHERE p.userId = :userId")
    List<Long> findChatIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT count(p.participantId) FROM Participant p WHERE p.chatId = :chatId")
    Long countByChatId(@Param("chatId") Long chatId);

    // Продвижение watermark прочтения. Только вперёд: если пользователь уже
    // прочитал дальше, повторный вызов ничего не меняет. Прочтение подразумевает
    // и доставку, поэтому last_delivered тоже продвигается.
    @Modifying
    @Query("""
        UPDATE Participant p
           SET p.lastReadMessageId = :messageId,
               p.lastReadAt = :at,
               p.lastDeliveredMessageId = :messageId,
               p.lastDeliveredAt = :at
         WHERE p.chatId = :chatId AND p.userId = :userId
           AND (p.lastReadMessageId IS NULL OR p.lastReadMessageId < :messageId)
    """)
    void advanceReadWatermark(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("at") LocalDateTime at
    );

    // Продвижение watermark доставки (серые галочки).
    @Modifying
    @Query("""
        UPDATE Participant p
           SET p.lastDeliveredMessageId = :messageId,
               p.lastDeliveredAt = :at
         WHERE p.chatId = :chatId AND p.userId = :userId
           AND (p.lastDeliveredMessageId IS NULL OR p.lastDeliveredMessageId < :messageId)
    """)
    void advanceDeliveredWatermark(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("at") LocalDateTime at
    );

    // Сколько участников прочитали сообщение messageId. Отправитель
    // считается прочитавшим своё сообщение.
    @Query("""
        SELECT COUNT(p) FROM Participant p
         WHERE p.chatId = :chatId
           AND (p.userId = :senderId
                OR (p.lastReadMessageId IS NOT NULL AND p.lastReadMessageId >= :messageId))
    """)
    long countReaders(
            @Param("chatId") Long chatId,
            @Param("messageId") Long messageId,
            @Param("senderId") Long senderId
    );
}