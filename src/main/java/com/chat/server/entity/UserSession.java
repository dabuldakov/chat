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
@Table(name = "user_sessions",
        indexes = {
                @Index(name = "idx_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_sessions_token", columnList = "token"),
                @Index(name = "idx_sessions_fcm_token", columnList = "fcm_token"),
                @Index(name = "idx_sessions_device_id", columnList = "device_id"),
                @Index(name = "idx_sessions_last_activity", columnList = "last_activity"),
                @Index(name = "idx_sessions_is_active", columnList = "is_active")
        })
public class UserSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "session_uuid", unique = true, updatable = false, nullable = false)
    private UUID sessionUuid = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token; // JWT токен

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken; // Firebase Cloud Messaging токен

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType; // ANDROID, IOS, WEB

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (sessionUuid == null) {
            sessionUuid = UUID.randomUUID();
        }
        lastActivity = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}