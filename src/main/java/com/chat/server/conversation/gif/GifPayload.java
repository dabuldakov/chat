package com.chat.server.conversation.gif;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Метаданные выбранной в приложении гифки. Поиск выполняет клиент напрямую у
 * провайдера (Klipy) — сервер провайдера не вызывает, только сохраняет ссылку
 * и ведёт «Мои»/«Этот чат».
 */
@Data
public class GifPayload {

    @NotBlank
    private String provider;

    @NotBlank
    private String providerGifId;

    private String title;

    @NotBlank
    private String url;

    private String previewUrl;

    private String mp4Url;

    private Integer width;

    private Integer height;

    private String rating;
}
