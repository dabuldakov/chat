package com.chat.server.repository;

import com.chat.server.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    void deleteByChatIdAndUserId(UUID chatId, UUID userId);
}