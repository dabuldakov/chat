package com.chat.server.report;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Жалоба пользователя на контент (сообщение, пользователя или чат).
 *
 * <p>Храним только идентификатор контента и короткий снимок текста: этого
 * достаточно для модерации и при этом не создаём вторую копию переписки.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "content_reports")
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Builder.Default
    @Column(name = "report_uuid", unique = true, updatable = false, nullable = false)
    private UUID reportUuid = UUID.randomUUID();

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Column(name = "content_uuid", nullable = false)
    private UUID contentUuid;

    /** Автор контента, на которого поступила жалоба (если известен). */
    @Column(name = "target_user_id")
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 1000)
    private String comment;

    @Column(name = "content_preview", length = 500)
    private String contentPreview;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (reportUuid == null) {
            reportUuid = UUID.randomUUID();
        }
        if (status == null) {
            status = Status.NEW;
        }
    }

    public enum ContentType {
        MESSAGE, USER, CHAT
    }

    public enum Reason {
        SPAM, ABUSE, VIOLENCE, ADULT, ILLEGAL, OTHER
    }

    public enum Status {
        NEW, REVIEWED, ACTION_TAKEN, REJECTED
    }
}
