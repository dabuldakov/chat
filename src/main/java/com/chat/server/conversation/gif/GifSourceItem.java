package com.chat.server.conversation.gif;

/**
 * Нормализованный результат внешнего источника гифок (например, Openverse/
 * Wikimedia), пригодный для импорта в наш каталог. Лицензия/атрибуция обязательны
 * для источников с CC-BY: их нужно хранить и показывать.
 */
public record GifSourceItem(
        String source,
        String sourceGifId,
        String title,
        String url,
        String previewUrl,
        Integer width,
        Integer height,
        String license,
        String licenseVersion,
        String creator,
        String attribution,
        String landingUrl
) {
}
