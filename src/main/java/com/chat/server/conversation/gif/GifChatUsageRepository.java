package com.chat.server.conversation.gif;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GifChatUsageRepository extends JpaRepository<GifChatUsage, Long> {

    Optional<GifChatUsage> findByChatIdAndGifId(Long chatId, Long gifId);

    List<GifChatUsage> findByChatIdOrderByLastUsedAtDesc(Long chatId, Pageable pageable);
}
