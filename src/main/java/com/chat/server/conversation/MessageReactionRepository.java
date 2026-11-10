package com.chat.server.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    List<MessageReaction> findByMessageIdIn(Collection<Long> messageIds);

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(
            Long messageId, Long userId, String emoji
    );
}
