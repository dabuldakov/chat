package com.chat.server.conversation.gif;

import java.util.UUID;

/**
 * Ссылка на сохранённую гифку. Всё, что нужно сообщению: наш ключ и данные для
 * рендера. Изолирует конвейер сообщений от устройства каталога/провайдеров.
 */
public record GifRef(
        UUID gifKey,
        String url,
        String previewUrl,
        String mp4Url,
        Integer width,
        Integer height
) {
}
