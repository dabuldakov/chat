package com.chat.server.conversation.gif;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GifUserUsageRepository extends JpaRepository<GifUserUsage, Long> {

    Optional<GifUserUsage> findByUserIdAndGifId(Long userId, Long gifId);

    List<GifUserUsage> findByUserIdOrderByLastUsedAtDesc(Long userId, Pageable pageable);
}
