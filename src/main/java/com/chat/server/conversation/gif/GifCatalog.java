package com.chat.server.conversation.gif;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Наша запись о гифке из провайдера (Klipy): метаданные и, при зеркалировании,
 * ссылка на копию в MinIO. Уникальность — по (provider, providerGifId), чтобы
 * одна и та же гифка не дублировалась в каталоге.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gif_catalog")
public class GifCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gif_id")
    private Long gifId;

    @Column(name = "gif_uuid", unique = true, updatable = false, nullable = false)
    private UUID gifUuid;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_gif_id", nullable = false, length = 255)
    private String providerGifId;

    @Column(length = 500)
    private String title;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "preview_url", length = 1000)
    private String previewUrl;

    private Integer width;

    private Integer height;

    @Column(length = 16)
    private String rating;

    @Column(name = "object_key", length = 500)
    private String objectKey;

    @Column(name = "public_url", length = 1000)
    private String publicUrl;

    @Column(nullable = false)
    private Boolean mirrored;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "use_count", nullable = false)
    private Long useCount;

    @Column(length = 64)
    private String license;

    @Column(name = "license_version", length = 32)
    private String licenseVersion;

    @Column(length = 255)
    private String creator;

    @Column(length = 1000)
    private String attribution;

    @Column(name = "landing_url", length = 1000)
    private String landingUrl;

    @Column(length = 1000)
    private String tags;

    @PrePersist
    protected void onCreate() {
        if (gifUuid == null) {
            gifUuid = UUID.randomUUID();
        }
        if (mirrored == null) {
            mirrored = false;
        }
        if (useCount == null) {
            useCount = 0L;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** URL, который отдаём клиентам: своё зеркало, если есть, иначе источник. */
    public String deliverableUrl() {
        return (Boolean.TRUE.equals(mirrored) && publicUrl != null) ? publicUrl : sourceUrl;
    }
}
