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
@Table(name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_message_id", columnList = "message_id"),
                @Index(name = "idx_attachments_uploader_id", columnList = "uploader_id"),
                @Index(name = "idx_attachments_type", columnList = "type"),
                @Index(name = "idx_attachments_created_at", columnList = "created_at")
        })
public class Attachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "attachment_uuid", unique = true, updatable = false, nullable = false)
    private UUID attachmentUuid = UUID.randomUUID();

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration")
    private Integer duration; // Длительность для видео/аудио в секундах

    @Column(name = "type", length = 20)
    @Enumerated(EnumType.STRING)
    private AttachmentType type;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "is_compressed")
    private Boolean isCompressed = false;

    @Column(name = "metadata")
    private String metadata; // JSON с дополнительными данными (exif и т.д.)

    public enum AttachmentType {
        IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (attachmentUuid == null) {
            attachmentUuid = UUID.randomUUID();
        }
    }

    public String getFileExtension() {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    public boolean isImage() {
        return type == AttachmentType.IMAGE;
    }

    public boolean isVideo() {
        return type == AttachmentType.VIDEO;
    }

    public boolean isAudio() {
        return type == AttachmentType.AUDIO;
    }
}