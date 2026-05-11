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
@Table(name = "message_statuses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_message_status", columnNames = {"message_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_msg_status_message_id", columnList = "message_id"),
                @Index(name = "idx_msg_status_user_id", columnList = "user_id"),
                @Index(name = "idx_msg_status_status", columnList = "status"),
                @Index(name = "idx_msg_status_updated_at", columnList = "updated_at")
        })
public class MessageStatus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "status_uuid", unique = true, updatable = false, nullable = false)
    private UUID statusUuid = UUID.randomUUID();

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status = DeliveryStatus.SENT;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public enum DeliveryStatus {
        SENT,      // Отправлено на сервер
        DELIVERED, // Доставлено на устройство
        READ,      // Прочитано
        FAILED     // Ошибка доставки
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (statusUuid == null) {
            statusUuid = UUID.randomUUID();
        }
        if (status == DeliveryStatus.DELIVERED && deliveredAt == null) {
            deliveredAt = LocalDateTime.now();
        }
        if (status == DeliveryStatus.READ && readAt == null) {
            readAt = LocalDateTime.now();
            deliveredAt = readAt;
        }
    }

    public void markAsDelivered() {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
        this.setUpdatedAt(LocalDateTime.now());
    }

    public void markAsRead() {
        this.status = DeliveryStatus.READ;
        this.readAt = LocalDateTime.now();
        if (this.deliveredAt == null) {
            this.deliveredAt = this.readAt;
        }
        this.setUpdatedAt(LocalDateTime.now());
    }

    public void markAsFailed() {
        this.status = DeliveryStatus.FAILED;
        this.setUpdatedAt(LocalDateTime.now());
    }

    public boolean isDelivered() {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.READ;
    }

    public boolean isRead() {
        return status == DeliveryStatus.READ;
    }
}