package com.chat.server.conversation;

import com.chat.server.identity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.chat.server.conversation.AttachmentDto;
@Data
@Builder
public class MessageDto {

    private UUID messageUuid;
    /** Монотонный идентификатор внутри БД: по нему сравниваются watermark-и статусов. */
    private Long messageId;
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
    // Явные JSON-имена: Jackson срезает префикс is у boolean-геттеров и отдавал
    // "edited"/"deleted"/"pinned", а клиент ждёт isEdited/isDeleted/isPinned.
    @Getter(onMethod_ = @JsonProperty("isEdited"))
    private boolean isEdited;
    @Getter(onMethod_ = @JsonProperty("isDeleted"))
    private boolean isDeleted;
    @Getter(onMethod_ = @JsonProperty("isPinned"))
    private boolean isPinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AttachmentDto> attachments;
    private MessageStatusDto status;
    private List<ReactionDto> reactions;
    /** 0..100 — сила «заряда» отправителя. */
    private int power;

    public static MessageDto fromEntity(Message message) {
        return MessageDto.builder()
                .messageUuid(message.getMessageUuid())
                .messageId(message.getMessageId())
                .text(message.getMessageText())
                .messageType(message.getMessageType().name())
                .isEdited(message.getIsEdited() != null && message.getIsEdited())
                .isDeleted(message.getIsDeleted() != null && message.getIsDeleted())
                .isPinned(message.getIsPinned() != null && message.getIsPinned())
                .power(message.getPower() == null ? 0 : message.getPower())
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