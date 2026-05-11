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
@Table(name = "contacts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_contact", columnNames = {"user_id", "contact_user_id"})
        },
        indexes = {
                @Index(name = "idx_contacts_user_id", columnList = "user_id"),
                @Index(name = "idx_contacts_contact_user_id", columnList = "contact_user_id")
        })
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "contact_uuid", unique = true, updatable = false, nullable = false)
    private UUID contactUuid = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contact_user_id", nullable = false)
    private Long contactUserId;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (contactUuid == null) {
            contactUuid = UUID.randomUUID();
        }
    }
}