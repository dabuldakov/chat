package com.chat.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "users",
        indexes = {
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_user_uuid", columnList = "user_uuid"),
                @Index(name = "idx_users_status", columnList = "status")
        })
@Where(clause = "is_deleted = false")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_uuid", unique = true, updatable = false, nullable = false)
    private UUID userUuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    public enum UserStatus {
        ACTIVE, INACTIVE, SUSPENDED, BANNED
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        return username;
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        if (isDeleted != null && isDeleted && deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }
}