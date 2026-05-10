package com.chat.server.repository;

import com.chat.server.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findByChatUuid(UUID uuid);

    @Query("""
        SELECT c FROM Chat c 
        WHERE c.chatId IN (
            SELECT p.chatId FROM Participant p 
            WHERE p.userId = :userId
        )
        ORDER BY c.updatedAt DESC
    """)
    List<Chat> findChatsByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM Chat c 
        WHERE c.chatType = 'PRIVATE' 
        AND c.chatId IN (
            SELECT p1.chatId FROM Participant p1 
            WHERE p1.userId = :userId1
            INTERSECT
            SELECT p2.chatId FROM Participant p2 
            WHERE p2.userId = :userId2
        )
    """)
    Optional<Chat> findPrivateChatBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}