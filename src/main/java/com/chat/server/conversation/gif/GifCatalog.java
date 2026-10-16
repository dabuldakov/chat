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
 * Наша запись о гифке из провайдера (Klipy): метаданные, которые пришли от
 * клиента при отправке. Уникальность — по (provider, providerGifId), чтобы одна
 * и та же гифка не дублировалась в каталоге. Медиа не храним — клиенты грузят
 * его по sourceUrl (требование провайдера).
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

    @Column(name = "mp4_url", length = 1000)
    private String mp4Url;

    private Integer width;

    private Integer height;

    @Column(length = 16)
    private String rating;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "use_count", nullable = false)
    private Long useCount;

    @PrePersist
    protected void onCreate() {
        if (gifUuid == null) {
            gifUuid = UUID.randomUUID();
        }
        if (useCount == null) {
            useCount = 0L;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
