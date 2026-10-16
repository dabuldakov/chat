package com.chat.server.conversation.gif;

import com.chat.server.exception.NotFoundException;
import com.chat.server.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Импорт гифок из свободных источников (Openverse/Wikimedia) с зеркалированием
 * в MinIO. Кэшируем у себя только то, что разрешено (CC0/public domain и т.п.),
 * и отдаём со своего API — клиенты грузят медиа отсюда.
 *
 * <p>Транзакцию на весь поиск не открываем: скачивание файлов — это сетевой I/O,
 * и держать под ним соединение из пула нельзя. Каждый {@code save} — своя
 * транзакция.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GifImportService {

    private final GifSourceRegistry sources;
    private final GifBinaryDownloader downloader;
    private final FileStorage fileStorage;
    private final GifCatalogRepository catalogRepository;

    @Value("${app.gifs.max-mirror-bytes:10485760}")
    private long maxMirrorBytes;

    /**
     * Библиотека гифок. Пустой запрос — просто список уже зеркалированных
     * (наша подборка), иначе поиск во внешних источниках с импортом.
     */
    public List<GifDto> library(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            return catalogRepository.findByMirroredTrueOrderByCreatedAtDesc(
                            org.springframework.data.domain.PageRequest.of(page, size))
                    .map(GifDto::fromEntity)
                    .getContent();
        }

        List<GifDto> result = new ArrayList<>();
        for (GifSourceProvider provider : sources.providers()) {
            for (GifSourceItem item : provider.search(query, page, size)) {
                GifCatalog gif = importItem(item, query);
                if (gif != null) {
                    result.add(GifDto.fromEntity(gif));
                }
            }
        }
        return result;
    }

    /** Отдаёт зеркалированный файл гифки. */
    @Transactional(readOnly = true)
    public Resource file(UUID gifUuid) {
        GifCatalog gif = catalogRepository.findByGifUuid(gifUuid)
                .orElseThrow(() -> new NotFoundException("GIF not found"));
        if (!Boolean.TRUE.equals(gif.getMirrored()) || gif.getObjectKey() == null) {
            throw new NotFoundException("GIF file not available");
        }
        return fileStorage.load(gif.getObjectKey());
    }

    private GifCatalog importItem(GifSourceItem item, String query) {
        GifCatalog existing = catalogRepository
                .findByProviderAndProviderGifId(item.source(), item.sourceGifId())
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getMirrored())) {
            return existing;
        }

        byte[] bytes;
        try {
            bytes = downloader.download(item.url());
        } catch (Exception e) {
            log.warn("Failed to download GIF {}: {}", item.url(), e.getMessage());
            return null;
        }
        if (bytes == null || bytes.length == 0 || bytes.length > maxMirrorBytes) {
            log.warn("GIF rejected by size/empty: {} ({} bytes)", item.url(),
                    bytes == null ? 0 : bytes.length);
            return null;
        }

        String objectKey = fileStorage.storeBytes(
                bytes, "image/gif", "gifs", item.source() + "_" + item.sourceGifId() + ".gif");

        GifCatalog gif = existing != null ? existing : GifCatalog.builder()
                .provider(item.source())
                .providerGifId(item.sourceGifId())
                .build();
        gif.setTitle(item.title());
        gif.setSourceUrl(item.url());
        gif.setWidth(item.width());
        gif.setHeight(item.height());
        gif.setLicense(item.license());
        gif.setLicenseVersion(item.licenseVersion());
        gif.setCreator(item.creator());
        gif.setAttribution(item.attribution());
        gif.setLandingUrl(item.landingUrl());
        gif.setTags(buildTags(item, query));
        gif.setObjectKey(objectKey);
        gif.setMirrored(true);

        GifCatalog saved = catalogRepository.save(gif);
        // Ссылка на наш файл (gif_uuid присваивается при сохранении).
        String publicUrl = "/api/gifs/file/" + saved.getGifUuid();
        saved.setPublicUrl(publicUrl);
        saved.setPreviewUrl(publicUrl);
        return catalogRepository.save(saved);
    }

    private static String buildTags(GifSourceItem item, String query) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (query != null && !query.isBlank()) {
            tags.add(query.trim().toLowerCase());
        }
        if (item.title() != null) {
            for (String word : item.title().toLowerCase().split("\\s+")) {
                if (word.length() > 2) {
                    tags.add(word);
                }
            }
        }
        String joined = String.join(" ", tags);
        return joined.length() > 1000 ? joined.substring(0, 1000) : joined;
    }
}
