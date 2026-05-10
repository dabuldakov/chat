package com.chat.server.dto.response;

import com.chat.server.entity.Message;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
}