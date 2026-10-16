package com.chat.server.conversation.gif;

import java.util.List;

/**
 * Порт каталога гифок — публичный API gif-модуля для конвейера сообщений.
 *
 * <p>Сообщения зависят только от этого интерфейса, а не от конкретной
 * реализации/хранилища. Благодаря этому gif-модуль можно вынести в отдельный
 * сервис (реализация порта станет удалённым клиентом), не трогая логику
 * сообщений и не меняя формат хранения (во вложении уже лежит наш gifKey).
 */
public interface GifCatalogPort {

    /** Сохраняет использованную гифку (каталог + usage) и возвращает ссылку. */
    GifRef recordSent(GifPayload payload, Long userId, Long chatId);

    /** «Мои» недавние гифки (по времени). */
    List<GifDto> recent(Long userId, int page, int size);

    /** Гифки чата (по времени). */
    List<GifDto> chatGifs(Long chatId, int page, int size);
}
