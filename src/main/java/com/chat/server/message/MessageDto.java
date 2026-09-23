package com.chat.server.message;

import com.chat.server.user.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.chat.server.attachment.AttachmentDto;
@Data
@Builder
public class MessageDto {

    private UUID messageUuid;
    private UUID chatUuid;
    private Long senderId;
    private UUID senderUuid;
    private String senderName;
    private String senderAvatar;
    private String text;
    private String messageType;
    private UUID replyToMessageUuid;
    private UUID forwardedFromMessageUuid;
    private UUID forwardedFromUserUuid;
    private boolean isEdited;
    private boolean isDeleted;
    private boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentDto> attachments;
    private MessageStatusDto status;

    public static MessageDto fromEntity(Message message) {
        return MessageDto.builder()
                .messageUuid(message.getMessageUuid())
                .text(message.getMessageText())
                .messageType(message.getMessageType().name())
                .isEdited(message.getIsEdited() != null && message.getIsEdited())
                .isDeleted(message.getIsDeleted() != null && message.getIsDeleted())
                .isPinned(message.getIsPinned() != null && message.getIsPinned())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    public static MessageDto fromEntity(Message message, User sender) {
        MessageDto dto = fromEntity(message);
        if (sender != null) {
            dto.setSenderId(sender.getUserId());
            dto.setSenderUuid(sender.getUserUuid());
            dto.setSenderName(sender.getFullName() != null ? sender.getFullName() : sender.getUsername());
            dto.setSenderAvatar(sender.getAvatarUrl());
        }
        return dto;
    }
}