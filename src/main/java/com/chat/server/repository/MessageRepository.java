package com.chat.server.repository;

import com.chat.server.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query(value = """
        SELECT m.* FROM messages m
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

    @Query(value = """
        SELECT COUNT(*) FROM messages m
        WHERE m.chat_id = :chatId
        AND m.is_deleted = false
    """, nativeQuery = true)
    long countMessagesInChat(@Param("chatId") Long chatId);

    @Query(value = """
        SELECT m.* FROM messages m
        WHERE m.chat_id = :chatId
        AND m.created_at > :since
        AND m.is_deleted = false
        ORDER BY m.created_at ASC
    """, nativeQuery = true)
    List<Message> findNewMessagesSince(
            @Param("chatId") Long chatId,
            @Param("since") java.time.LocalDateTime since
    );

    @Query(value = """
        SELECT COUNT(*) FROM messages m
        WHERE m.chat_id = :chatId
        AND m.message_id > :afterMessageId
        AND m.is_deleted = false
    """, nativeQuery = true)
    long countMessagesAfterId(
            @Param("chatId") Long chatId,
            @Param("afterMessageId") Long afterMessageId
    );
}