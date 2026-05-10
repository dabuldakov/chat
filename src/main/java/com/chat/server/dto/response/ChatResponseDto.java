package com.chat.server.dto.response;

import com.chat.server.entity.Chat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ChatResponseDto {

    private UUID chatUuid;
    private String chatType;
    private String title;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<UUID> participantIds;
    private Long participantCount;
    private MessagePreviewDto lastMessage;
    private long unreadCount;
    private boolean isArchived;

    public static ChatResponseDto fromEntity(Chat chat, List<UUID> participantIds) {
        return ChatResponseDto.builder()
                .chatUuid(chat.getChatUuid())
                .chatType(chat.getChatType().name())
                .title(chat.getTitle())
                .avatarUrl(chat.getAvatarUrl())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .participantIds(participantIds)
                .participantCount((long) participantIds.size())
                .isArchived(chat.getIsArchived() != null && chat.getIsArchived())
                .build();
    }
}