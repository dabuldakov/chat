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
@Table(name = "participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_participant_chat_user", columnNames = {"chat_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_participants_chat_id", columnList = "chat_id"),
                @Index(name = "idx_participants_user_id", columnList = "user_id"),
                @Index(name = "idx_participants_role", columnList = "role"),
                @Index(name = "idx_participants_joined_at", columnList = "joined_at")
        })
public class Participant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_uuid", nullable = false)
    private UUID userUUID;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "role", length = 20)
    @Enumerated(EnumType.STRING)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    @Column(name = "notification_settings")
    private String notificationSettings; // JSON строка с настройками

    public enum ParticipantRole {
        OWNER, ADMIN, MEMBER
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        joinedAt = LocalDateTime.now();
    }

    public boolean isMuted() {
        return mutedUntil != null && mutedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isOwner() {
        return role == ParticipantRole.OWNER;
    }

    public boolean isAdmin() {
        return role == ParticipantRole.ADMIN || role == ParticipantRole.OWNER;
    }
}