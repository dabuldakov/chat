package com.chat.server.conversation.gif.openverse;

import com.chat.server.conversation.gif.GifSourceItem;
import com.chat.server.conversation.gif.GifSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Источник свободных гифок — Openverse (агрегатор CC/public-domain медиа).
 * Запрашиваем только перераспространяемые лицензии (CC0 и public domain) и формат
 * gif — такое можно легально скачивать и хранить у себя.
 *
 * <p>Бин создаётся в {@link com.chat.server.conversation.gif.GifHttpConfig}
 * (RestClient собирается там), поэтому здесь нет @Component.
 */
@Slf4j
public class OpenverseGifSource implements GifSourceProvider {

    private static final String REDISTRIBUTABLE_LICENSES = "cc0,pdm";

    /**
     * Анонимные запросы Openverse ограничены страницей в 20 элементов
     * (иначе 401). После регистрации приложения (client credentials) лимит выше.
     */
    private static final int MAX_PAGE_SIZE = 20;

    private final RestClient client;

    public OpenverseGifSource(RestClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "openverse";
    }

    @Override
    public List<GifSourceItem> search(String query, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        OpenverseResponse response;
        try {
            response = client.get()
                    .uri(uri -> uri.path("/images/")
                            .queryParam("q", query)
                            .queryParam("license", REDISTRIBUTABLE_LICENSES)
                            .queryParam("extension", "gif")
                            .queryParam("page", page + 1) // Openverse-страницы с 1
                            .queryParam("page_size", pageSize)
                            .build())
                    .retrieve()
                    .body(OpenverseResponse.class);
        } catch (Exception e) {
            log.warn("Openverse search failed: {}", e.getMessage());
            return List.of();
        }
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(image -> image.url() != null)
                .map(image -> new GifSourceItem(
                        name(),
                        image.id(),
                        image.title(),
                        image.url(),
                        image.thumbnail(),
                        null,
                        null,
                        image.license(),
                        image.licenseVersion(),
                        image.creator(),
                        image.attribution(),
                        image.foreignLandingUrl()))
                .toList();
    }
}
