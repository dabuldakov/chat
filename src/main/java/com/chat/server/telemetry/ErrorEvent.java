package com.chat.server.telemetry;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Одна ошибка клиента. */
@Entity
@Table(name = "error_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "app_version", nullable = false, length = 32)
    private String appVersion;

    @Column(name = "version_code")
    private Integer versionCode;

    @Column(name = "build_type", length = 16)
    private String buildType;

    @Column(name = "device", length = 200)
    private String device;

    @Column(name = "os", length = 64)
    private String os;

    @Column(name = "level", nullable = false, length = 16)
    private String level;

    @Column(name = "tag", length = 200)
    private String tag;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "stack", columnDefinition = "TEXT")
    private String stack;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "buffer_overflowed", nullable = false)
    private boolean bufferOverflowed;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
