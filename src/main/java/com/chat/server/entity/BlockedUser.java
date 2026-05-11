package com.chat.server.entity;

import jakarta.persistence.*;
import lombok.Data;
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
@Table(name = "blocked_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_blocked", columnNames = {"user_id", "blocked_user_id"})
        },
        indexes = {
                @Index(name = "idx_blocked_user_id", columnList = "user_id"),
                @Index(name = "idx_blocked_blocked_id", columnList = "blocked_user_id")
        })
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "block_id")
    private Long blockId;

    @Column(name = "block_uuid", unique = true, updatable = false, nullable = false)
    private UUID blockUuid = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (blockUuid == null) {
            blockUuid = UUID.randomUUID();
        }
    }
}