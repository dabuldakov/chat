package com.chat.server.dto.response;

import com.chat.server.entity.Chat;
import com.chat.server.entity.Participant;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ChatDetailsResponseDto {

    private UUID chatUuid;
    private String chatType;
    private String title;
    private String description;
    private String avatarUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<UUID> participantIds;
    private Long participantCount;
    private long unreadCount;
    private MessagePreviewDto lastMessage;
    private boolean isArchived;

    public static ChatDetailsResponseDto toDto(Chat chat, List<UUID> participantIds, Long unreadCount) {
        return ChatDetailsResponseDto.builder()
                .chatUuid(chat.getChatUuid())
                .chatType(chat.getChatType().name())
                .title(chat.getTitle())
                .description(chat.getDescription())
                .avatarUrl(chat.getAvatarUrl())
                .createdBy(chat.getCreatedBy())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .participantIds(participantIds)
                .participantCount((long) participantIds.size())
                .unreadCount(unreadCount)
                .build();
    }
}