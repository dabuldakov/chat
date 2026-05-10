package com.chat.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "chats",
        indexes = {
                @Index(name = "idx_chats_chat_uuid", columnList = "chat_uuid"),
                @Index(name = "idx_chats_chat_type", columnList = "chat_type"),
                @Index(name = "idx_chats_created_by", columnList = "created_by"),
                @Index(name = "idx_chats_updated_at", columnList = "updated_at")
        })
public class Chat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "chat_uuid", unique = true, updatable = false, nullable = false)
    private UUID chatUuid = UUID.randomUUID();

    @Column(name = "chat_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChatType chatType;

    @Column(length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "is_private")
    private Boolean isPrivate = false;

    @Column(name = "is_archived")
    private Boolean isArchived = false;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_text", columnDefinition = "TEXT")
    private String lastMessageText;

    @Column(name = "last_message_sender_id")
    private Long lastMessageSenderId;

    @Column(name = "message_count")
    private Long messageCount = 0L;

    public enum ChatType {
        PRIVATE, GROUP, CHANNEL
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (chatUuid == null) {
            chatUuid = UUID.randomUUID();
        }
    }
}