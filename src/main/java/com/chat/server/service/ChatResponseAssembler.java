package com.chat.server.service;

import com.chat.server.dto.response.ChatResponseDto;
import com.chat.server.dto.response.MessagePreviewDto;
import com.chat.server.entity.Chat;
import com.chat.server.entity.Participant;
import com.chat.server.entity.User;
import com.chat.server.repository.ParticipantRepository;
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

    private final ParticipantRepository participantRepository;
    private final UserService userService;

    public ChatResponseDto toChatResponse(Chat chat, Long userId, long unreadCount) {
        List<Participant> participants = participantRepository.findAllByChatId(chat.getChatId());
        List<UUID> participantIds = participants.stream()
                .map(Participant::getUserUUID)
                .collect(Collectors.toList());

        Map<Long, User> usersById = usersById(participants);

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

    private Map<Long, User> usersById(List<Participant> participants) {
        List<Long> userIds = participants.stream()
                .map(Participant::getUserId)
                .distinct()
                .toList();
        return userService.getUsersByIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));
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