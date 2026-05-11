package com.chat.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_chat_id", columnList = "chat_id"),
                @Index(name = "idx_messages_sender_id", columnList = "sender_id"),
                @Index(name = "idx_messages_created_at", columnList = "created_at"),
                @Index(name = "idx_messages_chat_id_created_at", columnList = "chat_id, created_at DESC"),
                @Index(name = "idx_messages_message_uuid", columnList = "message_uuid"),
                @Index(name = "idx_messages_reply_to", columnList = "reply_to_message_id"),
                @Index(name = "idx_messages_is_deleted", columnList = "is_deleted")
        })
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "message_uuid", unique = true, updatable = false, nullable = false)
    private UUID messageUuid = UUID.randomUUID();

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "message_type", length = 20)
    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "forwarded_from_message_id")
    private Long forwardedFromMessageId;

    @Column(name = "forwarded_from_user_id")
    private Long forwardedFromUserId;

    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "edit_history")
    private String editHistory; // JSON строка с историей изменений

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "mentioned_users")
    private String mentionedUsers; // JSON массив ID пользователей

    @Column(name = "has_attachments")
    private Boolean hasAttachments = false;

    @Column(name = "metadata")
    private String metadata; // JSON строка с дополнительными данными

    public enum MessageType {
        TEXT, IMAGE, VIDEO, AUDIO, FILE, LOCATION, CONTACT, SYSTEM, DELETE
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (messageUuid == null) {
            messageUuid = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        if (isDeleted != null && isDeleted && deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isSystemMessage() {
        return messageType == MessageType.SYSTEM;
    }
}