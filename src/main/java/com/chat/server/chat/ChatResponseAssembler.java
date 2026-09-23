package com.chat.server.chat;

import com.chat.server.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Единственная ответственность — сборка {@link ChatResponseDto} из доменных сущностей.
 * Разгружает {@link ChatService}, который отвечает только за бизнес-логику чатов.
 */
@Component
@RequiredArgsConstructor
public class ChatResponseAssembler {

    /**
     * @param participants предзагруженные участники чата (без БД-запроса),
     * @param usersById    предзагруженная карта юзеров всех участников.
     */
    public ChatResponseDto toChatResponse(Chat chat, Long userId, List<Participant> participants,
                                          Map<Long, User> usersById, long unreadCount) {
        List<UUID> participantIds = participants.stream()
                .map(Participant::getUserUUID)
                .collect(Collectors.toList());

        String title = chat.getTitle();
        String avatarUrl = chat.getAvatarUrl();
        if (chat.getChatType() == Chat.ChatType.PRIVATE) {
            Optional<Long> otherId = participants.stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .map(Participant::getUserId)
                    .findFirst();
            if (otherId.isPresent()) {
                User other = usersById.get(otherId.get());
                if (other != null) {
                    title = other.getFullName();
                    avatarUrl = other.getAvatarUrl();
                }
            }
        }

        return ChatResponseDto.builder()
                .chatUuid(chat.getChatUuid())
                .chatType(chat.getChatType().name())
                .title(title)
                .avatarUrl(avatarUrl)
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .participantIds(participantIds)
                .participantCount((long) participantIds.size())
                .lastMessage(lastMessagePreview(chat, usersById))
                .unreadCount(unreadCount)
                .isArchived(chat.getIsArchived() != null && chat.getIsArchived())
                .build();
    }

    private MessagePreviewDto lastMessagePreview(Chat chat, Map<Long, User> usersById) {
        if (chat.getLastMessageText() == null && chat.getLastMessageSenderId() == null) {
            return null;
        }
        User sender = usersById.get(chat.getLastMessageSenderId());
        return MessagePreviewDto.builder()
                .text(chat.getLastMessageText())
                .senderId(chat.getLastMessageSenderId())
                .senderName(sender != null ? sender.getFullName() : null)
                .createdAt(chat.getUpdatedAt())
                .build();
    }
}