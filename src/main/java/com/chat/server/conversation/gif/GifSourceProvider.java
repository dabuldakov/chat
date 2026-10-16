package com.chat.server.conversation.gif;

import java.util.List;

/**
 * Порт внешнего серверного источника гифок, которые можно **легально хранить**
 * (public domain / CC0 / CC-BY с атрибуцией) — например, Openverse, Wikimedia
 * Commons. Реализации импортируют найденное в наш каталог (MinIO + метаданные).
 *
 * <p>Klipy сюда не входит: по его правилам поиск идёт с клиента, сервер его не
 * проксирует.
 */
public interface GifSourceProvider {

    /** Имя источника (openverse, wikimedia, …). */
    String name();

    /** Поиск во внешнем источнике. */
    List<GifSourceItem> search(String query, int page, int size);
}
