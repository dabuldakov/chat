package com.chat.server.conversation.gif;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Реестр внешних источников гифок. Spring подставляет все бины
 * {@link GifSourceProvider}; список может быть пустым (источники отключены).
 * Точка расширения: добавить Openverse/Wikimedia-провайдер, не трогая остальное.
 */
@Component
@RequiredArgsConstructor
public class GifSourceRegistry {

    private final List<GifSourceProvider> providers;

    public List<GifSourceProvider> providers() {
        return List.copyOf(providers);
    }

    public Optional<GifSourceProvider> byName(String name) {
        return providers.stream().filter(provider -> provider.name().equals(name)).findFirst();
    }
}
