package com.chat.server.conversation.gif;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Каталог и использование гифок. Поиск выполняет клиент у провайдера напрямую;
 * сервер лишь сохраняет выбранную гифку (метаданные) и ведёт списки
 * «Мои»/«Этот чат» (порядок по времени последнего использования).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GifService implements GifCatalogPort {

    private final GifCatalogRepository catalogRepository;
    private final GifUserUsageRepository userUsageRepository;
    private final GifChatUsageRepository chatUsageRepository;

    /** Идемпотентно создаёт/обновляет запись каталога по (provider, providerGifId). */
    @Transactional
    public GifCatalog upsert(GifPayload payload) {
        GifCatalog gif = catalogRepository
                .findByProviderAndProviderGifId(payload.getProvider(), payload.getProviderGifId())
                .orElseGet(() -> GifCatalog.builder()
                        .provider(payload.getProvider())
                        .providerGifId(payload.getProviderGifId())
                        .build());

        gif.setTitle(payload.getTitle());
        gif.setSourceUrl(payload.getUrl());
        gif.setPreviewUrl(payload.getPreviewUrl());
        gif.setMp4Url(payload.getMp4Url());
        gif.setWidth(payload.getWidth());
        gif.setHeight(payload.getHeight());
        gif.setRating(payload.getRating());
        return catalogRepository.save(gif);
    }

    /** Сохраняет гифку и отмечает использование пользователем и в чате. */
    @Override
    @Transactional
    public GifRef recordSent(GifPayload payload, Long userId, Long chatId) {
        GifCatalog gif = upsert(payload);
        LocalDateTime now = LocalDateTime.now();

        gif.setLastUsedAt(now);
        gif.setUseCount((gif.getUseCount() == null ? 0L : gif.getUseCount()) + 1);
        catalogRepository.save(gif);

        GifUserUsage userUsage = userUsageRepository.findByUserIdAndGifId(userId, gif.getGifId())
                .orElseGet(() -> GifUserUsage.builder()
                        .userId(userId).gifId(gif.getGifId()).useCount(0L).build());
        userUsage.setLastUsedAt(now);
        userUsage.setUseCount(userUsage.getUseCount() + 1);
        userUsageRepository.save(userUsage);

        if (chatId != null) {
            GifChatUsage chatUsage = chatUsageRepository.findByChatIdAndGifId(chatId, gif.getGifId())
                    .orElseGet(() -> GifChatUsage.builder()
                            .chatId(chatId).gifId(gif.getGifId()).useCount(0L).build());
            chatUsage.setLastUsedAt(now);
            chatUsage.setUseCount(chatUsage.getUseCount() + 1);
            chatUsageRepository.save(chatUsage);
        }
        return toRef(gif);
    }

    private static GifRef toRef(GifCatalog gif) {
        return new GifRef(
                gif.getGifUuid(),
                gif.deliverableUrl(),
                gif.getPreviewUrl(),
                gif.getMp4Url(),
                gif.getWidth(),
                gif.getHeight());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GifDto> recent(Long userId, int page, int size) {
        List<Long> ids = userUsageRepository
                .findByUserIdOrderByLastUsedAtDesc(userId, PageRequest.of(page, size))
                .stream().map(GifUserUsage::getGifId).toList();
        return toDtos(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GifDto> chatGifs(Long chatId, int page, int size) {
        List<Long> ids = chatUsageRepository
                .findByChatIdOrderByLastUsedAtDesc(chatId, PageRequest.of(page, size))
                .stream().map(GifChatUsage::getGifId).toList();
        return toDtos(ids);
    }

    private List<GifDto> toDtos(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, GifCatalog> byId = catalogRepository.findByGifIdIn(ids).stream()
                .collect(Collectors.toMap(GifCatalog::getGifId, gif -> gif));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(GifDto::fromEntity)
                .toList();
    }
}
