package com.chat.server.conversation;

import com.chat.server.identity.PresenceVisibilityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Видимость присутствия: только участники общих чатов. */
@Component
@RequiredArgsConstructor
public class ChatPresenceVisibility implements PresenceVisibilityPort {

    private final ParticipantRepository participantRepository;

    @Override
    public Set<Long> visibleUserIds(Long viewerId, Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(userIds);
        List<Long> shared = participantRepository.findSharedChatUserIds(viewerId, ids);
        Set<Long> result = new HashSet<>(shared);
        result.add(viewerId);
        return result;
    }
}
